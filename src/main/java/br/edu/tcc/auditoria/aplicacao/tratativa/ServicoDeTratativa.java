package br.edu.tcc.auditoria.aplicacao.tratativa;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.RepositorioTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.time.Clock;
import java.util.UUID;

// Serviço que registra a decisão de uma pessoa sobre um apontamento, derivando a chave de tratativa do próprio apontamento; não apaga nem esconde o apontamento.
public final class ServicoDeTratativa {

    private final ConsultaDeAchados consulta;
    private final RepositorioTratativa repositorio;
    private final Clock relogio;

    // Construtor do serviço de tratativa, que recebe a consulta de apontamentos, o repositório de tratativas e o relógio.
    public ServicoDeTratativa(
            ConsultaDeAchados consulta, RepositorioTratativa repositorio, Clock relogio) {
        this.consulta = exigir(consulta, "a consulta de apontamentos");
        this.repositorio = exigir(repositorio, "o repositório de tratativas");
        this.relogio = exigir(relogio, "o relógio");
    }

    // Registra a decisão sobre o apontamento indicado; tratar de novo substitui a decisão anterior.
    public Tratativa registrar(UUID achadoId, DecisaoDeTratativa decisao, String justificativa) {
        if (achadoId == null) {
            throw new TratativaInvalida("Não foi informado qual apontamento tratar.");
        }
        AchadoRegistrado registrado = consulta.porId(achadoId).orElseThrow(() ->
                new TratativaInvalida(
                        "Não há apontamento com o identificador %s.".formatted(achadoId)));

        ChaveDeTratativa chave = ChaveDeTratativa.de(registrado.hashDoItem(), registrado.achado());
        Tratativa tratativa = new Tratativa(chave, decisao, justificativa, relogio.instant());
        repositorio.salvar(tratativa);
        return tratativa;
    }

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new TratativaInvalida("O serviço de tratativa precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
