package br.edu.tcc.auditoria.aplicacao.identidade;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Serviço que cria, altera, exclui e autentica usuários. Aqui moram as duas regras que não podem depender da tela: o sistema nunca fica sem administrador ativo, e quem já registrou tratativa é desativado em vez de apagado.
public final class ServicoDeUsuarios {

    // Senha usada só para gerar um hash de comparação quando o login não existe, para o tempo de resposta não revelar quais logins existem.
    private static final SenhaInformada SENHA_DE_COMPARACAO =
            new SenhaInformada("senha-de-comparacao-que-ninguem-usa");

    private final RepositorioDeUsuarios repositorio;
    private final CodificadorDeSenha codificador;
    private final Clock relogio;
    private volatile HashDeSenha hashDeComparacao;

    // Construtor que recebe o repositório de usuários, o codificador de senha e o relógio.
    public ServicoDeUsuarios(
            RepositorioDeUsuarios repositorio, CodificadorDeSenha codificador, Clock relogio) {
        this.repositorio = exigir(repositorio, "o repositório de usuários");
        this.codificador = exigir(codificador, "o codificador de senha");
        this.relogio = exigir(relogio, "o relógio");
    }

    // Cria um usuário ativo com a senha informada; recusa login repetido e senha fraca.
    public Usuario criar(String login, String nome, Perfil perfil, SenhaInformada senha) {
        exigir(senha, "a senha").exigirQueSirvaComoSenhaNova();
        String padronizado = Usuario.normalizarLogin(login);
        Usuario novo = new Usuario(
                UUID.randomUUID(), padronizado, nome, perfil, true, relogio.instant(), Optional.empty());
        HashDeSenha hash = codificador.codificar(senha);

        return repositorio.comUsuariosTravados(() -> {
            if (repositorio.porLogin(padronizado).isPresent()) {
                throw new IdentidadeInvalida(
                        "Já existe usuário com o login \"%s\".".formatted(padronizado));
            }
            repositorio.inserir(novo, hash);
            return novo;
        });
    }

    // Lista todos os usuários, inclusive os desativados, para quem decidiu algo continuar visível.
    public List<Usuario> listar() {
        return repositorio.todos();
    }

    // Busca um usuário; recusa se não existir.
    public Usuario buscar(UUID id) {
        return repositorio.porId(exigir(id, "o identificador do usuário"))
                .orElseThrow(() -> naoEncontrado(id));
    }

    // Aplica a alteração pedida; recusa rebaixar ou desativar o último administrador ativo.
    public Usuario alterar(UUID id, AlteracaoDeUsuario alteracao) {
        exigir(alteracao, "a alteração");
        alteracao.novaSenha().ifPresent(SenhaInformada::exigirQueSirvaComoSenhaNova);
        Optional<HashDeSenha> novoHash = alteracao.novaSenha().map(codificador::codificar);

        return repositorio.comUsuariosTravados(() -> {
            Usuario atual = buscar(id);
            Usuario alterado = atual;
            if (alteracao.nome().isPresent()) {
                alterado = alterado.comNome(alteracao.nome().get());
            }
            if (alteracao.perfil().isPresent()) {
                alterado = alterado.comPerfil(alteracao.perfil().get());
            }
            if (alteracao.ativo().isPresent()) {
                alterado = alteracao.ativo().get()
                        ? alterado.reativado()
                        : alterado.desativado(relogio.instant());
            }

            if (atual.ehAdministradorAtivo() && !alterado.ehAdministradorAtivo()) {
                recusarSeForOUltimoAdministrador(atual,
                        alterado.ativo() ? "rebaixado para " + alterado.perfil().rotulo() : "desativado");
            }

            repositorio.atualizar(alterado);
            novoHash.ifPresent(hash -> repositorio.trocarHash(id, hash));
            return alterado;
        });
    }

    // Exclui o usuário. Quem já registrou tratativa ou correção é desativado, e não apagado; o último administrador ativo não sai.
    public ResultadoDaExclusao excluir(UUID id) {
        return repositorio.comUsuariosTravados(() -> {
            Usuario atual = buscar(id);
            if (atual.ehAdministradorAtivo()) {
                recusarSeForOUltimoAdministrador(atual, "excluído");
            }
            if (repositorio.temRegistroAtribuido(id)) {
                repositorio.atualizar(atual.desativado(relogio.instant()));
                return ResultadoDaExclusao.DESATIVADO;
            }
            repositorio.remover(id);
            return ResultadoDaExclusao.REMOVIDO;
        });
    }

    // Troca a própria senha, exigindo a senha atual.
    public void trocarPropriaSenha(UUID id, SenhaInformada atual, SenhaInformada nova) {
        exigir(atual, "a senha atual");
        exigir(nova, "a senha nova").exigirQueSirvaComoSenhaNova();
        HashDeSenha gravado = repositorio.hashDe(id).orElseThrow(() -> naoEncontrado(id));
        if (!codificador.confere(atual, gravado)) {
            throw new AcessoNegado("A senha atual não confere.");
        }
        repositorio.trocarHash(id, codificador.codificar(nova));
    }

    // Confere login e senha e devolve o usuário se estiverem certos e ele estiver ativo. A resposta é a mesma para login inexistente, senha errada e usuário desativado.
    public Optional<Usuario> autenticar(String login, SenhaInformada senha) {
        if (login == null || login.isBlank() || senha == null) {
            return Optional.empty();
        }
        Optional<Usuario> encontrado;
        try {
            encontrado = repositorio.porLogin(Usuario.normalizarLogin(login));
        } catch (IdentidadeInvalida loginForaDoFormato) {
            encontrado = Optional.empty();
        }

        Optional<HashDeSenha> hash = encontrado.flatMap(usuario -> repositorio.hashDe(usuario.id()));
        // Confere sempre, mesmo sem usuário, para quem mede o tempo não descobrir que o login não existe.
        boolean senhaConfere = codificador.confere(senha, hash.orElseGet(this::hashDeComparacao));

        if (encontrado.isEmpty() || hash.isEmpty() || !senhaConfere || !encontrado.get().ativo()) {
            return Optional.empty();
        }
        return encontrado;
    }

    // Cria o administrador pedido pela linha de comando, ou, se o login já existe, redefine a senha, põe o perfil de administrador e reativa. É a porta de entrada da instalação e a recuperação de acesso, já que não há recuperação por e-mail.
    public ResultadoDoAdministradorLocal garantirAdministrador(
            String login, String nome, SenhaInformada senha) {
        exigir(senha, "a senha").exigirQueSirvaComoSenhaNova();
        String padronizado = Usuario.normalizarLogin(login);
        HashDeSenha hash = codificador.codificar(senha);

        return repositorio.comUsuariosTravados(() -> {
            Optional<Usuario> existente = repositorio.porLogin(padronizado);
            if (existente.isEmpty()) {
                Usuario novo = new Usuario(UUID.randomUUID(), padronizado, nome, Perfil.ADMINISTRADOR,
                        true, relogio.instant(), Optional.empty());
                repositorio.inserir(novo, hash);
                return new ResultadoDoAdministradorLocal(novo, true);
            }
            Usuario promovido = existente.get().comPerfil(Perfil.ADMINISTRADOR).reativado();
            repositorio.atualizar(promovido);
            repositorio.trocarHash(promovido.id(), hash);
            return new ResultadoDoAdministradorLocal(promovido, false);
        });
    }

    // Representa o que o comando criar-administrador fez: criou um usuário novo ou recuperou um que já existia.
    public record ResultadoDoAdministradorLocal(Usuario usuario, boolean criado) {
    }

    // Método auxiliar que recusa a mudança quando o usuário é o único administrador ativo.
    private void recusarSeForOUltimoAdministrador(Usuario usuario, String oQueAconteceria) {
        if (repositorio.administradoresAtivos() <= 1) {
            throw new UltimoAdministrador(
                    ("\"%s\" é o único administrador ativo e não pode ser %s. O sistema nunca fica sem "
                            + "administrador: sem ele, ninguém cria usuário nem corrige carga de catálogo "
                            + "pela interface. Promova outra pessoa antes.")
                            .formatted(usuario.login(), oQueAconteceria));
        }
    }

    // Método auxiliar que calcula uma vez o hash usado quando o login não existe.
    private HashDeSenha hashDeComparacao() {
        HashDeSenha calculado = hashDeComparacao;
        if (calculado == null) {
            calculado = codificador.codificar(SENHA_DE_COMPARACAO);
            hashDeComparacao = calculado;
        }
        return calculado;
    }

    // Método auxiliar que monta a recusa de usuário inexistente.
    private static UsuarioNaoEncontrado naoEncontrado(UUID id) {
        return new UsuarioNaoEncontrado("Não há usuário com o identificador %s.".formatted(id));
    }

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new IdentidadeInvalida("Falta %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
