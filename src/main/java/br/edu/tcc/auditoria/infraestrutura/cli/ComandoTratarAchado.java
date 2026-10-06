package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.identidade.IdentidadeInvalida;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;
import br.edu.tcc.auditoria.aplicacao.tratativa.RegistroDeTratativa;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativaAtribuida;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Classe do comando tratar-achado, que registra a decisão de uma pessoa sobre um apontamento, com justificativa obrigatória. A pessoa informa o identificador da listagem; a chave da tratativa sai do próprio apontamento. Emenda da Etapa 12: o comando passou a exigir --usuario e a pedir a senha no terminal, e a tratativa grava quem decidiu. Antes gravava sem autor, pelo ServicoDeTratativa. Consequência aceita: sem terminal interativo, o comando recusa, e não serve para roteiro automático.
@Component
class ComandoTratarAchado implements Comando {

    static final String NOME = "tratar-achado";

    private static final String OPCAO_ACHADO = "achado";
    private static final String OPCAO_DECISAO = "decisao";
    private static final String OPCAO_JUSTIFICATIVA = "justificativa";
    private static final String OPCAO_USUARIO = "usuario";

    private final ServicoDeTratativaAtribuida servico;
    private final ServicoDeUsuarios usuarios;
    private final LeitorDeSenha senhas;
    private final Saida saida;

    // Construtor que recebe o serviço de tratativa com autor, os usuários, quem pede a senha no terminal e a saída. Mudou na Etapa 12: antes recebia o ServicoDeTratativa, que grava sem autor.
    ComandoTratarAchado(
            ServicoDeTratativaAtribuida servico,
            ServicoDeUsuarios usuarios,
            LeitorDeSenha senhas,
            Saida saida) {
        this.servico = servico;
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
        return "Aceita ou refuta um apontamento, com justificativa.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --usuario=<login> --achado=<id> --decisao=<%s> --justificativa=<texto>

                  --usuario        login de quem decide. A senha é pedida no
                                   terminal, sem aparecer na tela. Perfil de
                                   consulta não registra tratativa.

                  --achado         identificador do apontamento, como sai em "%s"
                  --decisao        %s se a incoerência procede,
                                   %s se o documento está correto
                  --justificativa  por que, em texto livre. Obrigatória.

                A tratativa vale para esta versão da regra. Se a regra mudar de
                versão, o apontamento reabre e precisa de decisão nova, porque o
                critério que o gerou passou a ser outro.
                """.formatted(
                        NOME,
                        String.join("|",
                                Arrays.stream(DecisaoDeTratativa.values()).map(Enum::name).toList()),
                        ComandoListarAchados.NOME,
                        DecisaoDeTratativa.ACEITO,
                        DecisaoDeTratativa.REFUTADO);
    }

    // Confere login e senha, registra a decisão em nome de quem entrou e mostra para que versão da regra ela vale.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_USUARIO, OPCAO_ACHADO, OPCAO_DECISAO, OPCAO_JUSTIFICATIVA));

        String login = argumentos.textoObrigatorio(OPCAO_USUARIO);
        UUID achadoId = argumentos.identificadorObrigatorio(OPCAO_ACHADO);
        DecisaoDeTratativa decisao = decisao(argumentos.textoObrigatorio(OPCAO_DECISAO));
        String justificativa = argumentos.textoObrigatorio(OPCAO_JUSTIFICATIVA);

        String senha = senhas.pedir("Senha de %s: ".formatted(login))
                .orElseThrow(ComandoCriarAdministrador::semTerminal);
        Usuario autor = usuarios.autenticar(login, new SenhaInformada(senha))
                .orElseThrow(() -> new UsoInvalido("Login ou senha inválidos. Nada foi gravado."));

        RegistroDeTratativa registro;
        try {
            registro = servico.registrar(achadoId, decisao, justificativa, autor.id());
        } catch (IdentidadeInvalida recusada) {
            throw new UsoInvalido(recusada.getMessage());
        }
        Tratativa tratativa = registro.tratativa();

        saida.linha("Apontamento %s marcado como %s.", achadoId, tratativa.decisao());
        saida.linha("  decidido por: %s (%s)", autor.nome(), autor.login());
        saida.linha("  justificativa: %s", tratativa.justificativa());
        saida.linha("  vale para a regra %s na versão %s.",
                tratativa.regraId(), tratativa.regraVersao());
    }

    // Método auxiliar que converte o texto na decisão; recusa valor desconhecido.
    private static DecisaoDeTratativa decisao(String informada) {
        return Arrays.stream(DecisaoDeTratativa.values())
                .filter(decisao -> decisao.name().equalsIgnoreCase(informada))
                .findFirst()
                .orElseThrow(() -> new UsoInvalido(
                        "Decisão desconhecida: \"%s\". Valores aceitos: %s."
                                .formatted(informada, Arrays.toString(DecisaoDeTratativa.values()))));
    }
}
