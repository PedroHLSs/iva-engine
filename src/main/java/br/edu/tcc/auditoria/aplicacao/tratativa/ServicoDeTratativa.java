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

/**
 * Caso de uso: registrar a decisão de uma pessoa sobre um apontamento.
 *
 * <p>Quem trata informa o identificador do apontamento — que é o que a listagem
 * mostra —, a decisão e a justificativa. O serviço reencontra o apontamento e
 * deriva dele a chave de tratativa: resumo do item, identificador da regra e
 * versão da regra. Quem trata não digita a chave, e não pode digitá-la errado.</p>
 *
 * <h2>O que a tratativa não faz</h2>
 *
 * <p>Não apaga o apontamento, não o remove da listagem e não altera o recibo da
 * execução que o gerou. Ela acrescenta uma decisão ao lado dele. E não se
 * transfere entre versões de regra: se a regra mudar de versão, o apontamento
 * reabre e precisa de decisão nova, porque o critério mudou — ver
 * {@link ChaveDeTratativa}.</p>
 */
public final class ServicoDeTratativa {

    private final ConsultaDeAchados consulta;
    private final RepositorioTratativa repositorio;
    private final Clock relogio;

    public ServicoDeTratativa(
            ConsultaDeAchados consulta, RepositorioTratativa repositorio, Clock relogio) {
        this.consulta = exigir(consulta, "a consulta de apontamentos");
        this.repositorio = exigir(repositorio, "o repositório de tratativas");
        this.relogio = exigir(relogio, "o relógio");
    }

    /**
     * Registra a decisão sobre o apontamento indicado.
     *
     * <p>Tratar de novo o mesmo apontamento substitui a decisão anterior: fica
     * valendo a última, com a justificativa dela.</p>
     *
     * @throws TratativaInvalida se o apontamento não existe, ou se falta decisão
     *                           ou justificativa
     */
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

    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new TratativaInvalida("O serviço de tratativa precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
