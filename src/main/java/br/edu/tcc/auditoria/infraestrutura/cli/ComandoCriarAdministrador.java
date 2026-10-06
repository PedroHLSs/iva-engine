package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.identidade.IdentidadeInvalida;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios.ResultadoDoAdministradorLocal;

import org.springframework.stereotype.Component;

import java.util.List;

// Classe do comando criar-administrador, a porta de entrada da instalação: nenhuma migration cria usuário nem senha padrão. Também é a recuperação de acesso, já que não há recuperação por e-mail: se o login já existe, redefine a senha, põe o perfil de administrador e reativa. Só roda na máquina onde o sistema está. Acrescentado na Etapa 12.
@Component
class ComandoCriarAdministrador implements Comando {

    static final String NOME = "criar-administrador";

    private static final String OPCAO_LOGIN = "login";
    private static final String OPCAO_NOME = "nome";

    private final ServicoDeUsuarios usuarios;
    private final LeitorDeSenha senhas;
    private final Saida saida;

    // Construtor que recebe o serviço de usuários, quem pede a senha no terminal e a saída.
    ComandoCriarAdministrador(ServicoDeUsuarios usuarios, LeitorDeSenha senhas, Saida saida) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Cria um administrador, ou recupera o acesso de um login que já existe.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --login=<login> --nome=<nome>

                  --login  login do administrador. Se já existir, a senha é redefinida,
                           o perfil vira administrador e o usuário é reativado.
                  --nome   nome da pessoa, como aparece nas tratativas que ela registrar.

                A senha é pedida no terminal, duas vezes, sem aparecer na tela. Ela
                nunca entra como opção: opção fica no histórico do terminal.
                Mínimo de %d caracteres.
                """.formatted(NOME, SenhaInformada.TAMANHO_MINIMO);
    }

    // Pede a senha duas vezes e cria ou recupera o administrador.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_LOGIN, OPCAO_NOME));
        String login = argumentos.textoObrigatorio(OPCAO_LOGIN);
        String nome = argumentos.textoObrigatorio(OPCAO_NOME);

        String senha = senhas.pedir("Senha: ").orElseThrow(ComandoCriarAdministrador::semTerminal);
        String confirmacao = senhas.pedir("Repita a senha: ").orElseThrow(ComandoCriarAdministrador::semTerminal);
        if (!senha.equals(confirmacao)) {
            throw new UsoInvalido("As duas senhas digitadas não são iguais. Nada foi gravado.");
        }

        ResultadoDoAdministradorLocal resultado;
        try {
            resultado = usuarios.garantirAdministrador(login, nome, new SenhaInformada(senha));
        } catch (IdentidadeInvalida invalida) {
            throw new UsoInvalido(invalida.getMessage());
        }

        if (resultado.criado()) {
            saida.linha("Administrador \"%s\" criado.", resultado.usuario().login());
        } else {
            saida.linha("O login \"%s\" já existia: a senha foi redefinida, o perfil é administrador e o "
                    + "usuário está ativo.", resultado.usuario().login());
        }
    }

    // Método auxiliar que monta a recusa quando não há terminal para pedir a senha.
    static UsoInvalido semTerminal() {
        return new UsoInvalido(
                "Não há terminal interativo para pedir a senha. Rode o comando direto no terminal, sem "
                        + "redirecionar a entrada.");
    }
}
