package br.edu.tcc.auditoria.aplicacao.tratativa;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.identidade.AcessoNegado;
import br.edu.tcc.auditoria.aplicacao.identidade.RepositorioDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

// Serviço que registra a decisão de uma pessoa identificada sobre um apontamento, gravando quem decidiu e quando. Acrescentado na Etapa 12 ao lado do ServicoDeTratativa, que continua existindo e grava sem autor; nenhuma entrada do sistema usa mais aquele para gravar.
public final class ServicoDeTratativaAtribuida {

    private final ConsultaDeAchados consulta;
    private final HistoricoDeTratativas historico;
    private final RepositorioDeUsuarios usuarios;
    private final Clock relogio;

    // Construtor que recebe a consulta de apontamentos, o histórico de tratativas, os usuários e o relógio.
    public ServicoDeTratativaAtribuida(
            ConsultaDeAchados consulta,
            HistoricoDeTratativas historico,
            RepositorioDeUsuarios usuarios,
            Clock relogio) {
        this.consulta = exigir(consulta, "a consulta de apontamentos");
        this.historico = exigir(historico, "o histórico de tratativas");
        this.usuarios = exigir(usuarios, "o repositório de usuários");
        this.relogio = exigir(relogio, "o relógio");
    }

    // Registra a decisão em nome do autor; recusa autor inexistente, desativado ou de perfil que não trata apontamento.
    public RegistroDeTratativa registrar(
            UUID achadoId, DecisaoDeTratativa decisao, String justificativa, UUID autorId) {
        Usuario autor = usuarios.porId(exigir(autorId, "o autor"))
                .orElseThrow(() -> new AcessoNegado("O autor da tratativa não existe mais."));
        if (!autor.ativo()) {
            throw new AcessoNegado(
                    "O usuário \"%s\" está desativado e não registra tratativa.".formatted(autor.login()));
        }
        if (!autor.perfil().podeRegistrarTratativa()) {
            throw new AcessoNegado(
                    "O perfil %s não registra tratativa: é somente leitura."
                            .formatted(autor.perfil().rotulo()));
        }

        ChaveDeTratativa chave = chaveDo(achadoId);
        Tratativa tratativa = new Tratativa(chave, decisao, justificativa, relogio.instant());
        return historico.gravar(tratativa, autor.id());
    }

    // Devolve o histórico de decisões do apontamento, do mais antigo ao mais recente.
    public List<RegistroDeTratativa> historicoDo(UUID achadoId) {
        return historico.historico(chaveDo(achadoId));
    }

    // Método auxiliar que acha o apontamento e deriva a chave da tratativa, que é do conteúdo e não da linha.
    private ChaveDeTratativa chaveDo(UUID achadoId) {
        if (achadoId == null) {
            throw new TratativaInvalida("Não foi informado qual apontamento tratar.");
        }
        AchadoRegistrado registrado = consulta.porId(achadoId).orElseThrow(() ->
                new TratativaInvalida(
                        "Não há apontamento com o identificador %s.".formatted(achadoId)));
        return ChaveDeTratativa.de(registrado.hashDoItem(), registrado.achado());
    }

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new TratativaInvalida("O serviço de tratativa precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
