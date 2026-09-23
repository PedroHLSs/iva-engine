package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;

import java.time.Instant;

// Representa a decisão de uma pessoa sobre um apontamento, com justificativa obrigatória. Fica presa à chave (item, regra e versão), e não à linha do banco, por isso continua valendo quando o lote é reprocessado.
public record Tratativa(
        ChaveDeTratativa chave,
        DecisaoDeTratativa decisao,
        String justificativa,
        Instant registradoEm) {

    // Valida que a tratativa tenha chave, decisão, justificativa preenchida e data de registro.
    public Tratativa {
        if (chave == null) {
            throw new TratativaInvalida("A tratativa precisa dizer o que está tratando.");
        }
        if (decisao == null) {
            throw new TratativaInvalida(
                    "A tratativa precisa de decisão: %s ou %s."
                            .formatted(DecisaoDeTratativa.ACEITO, DecisaoDeTratativa.REFUTADO));
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new TratativaInvalida(
                    "A tratativa precisa de justificativa. Apontamento tratado sem razão registrada é "
                            + "apontamento apagado, e o relatório deixa de dizer por que a incoerência "
                            + "não é mais incoerência.");
        }
        if (registradoEm == null) {
            throw new TratativaInvalida("A tratativa precisa registrar quando foi dada.");
        }
    }

    // Indica se esta tratativa vale para o apontamento e o item indicados.
    public boolean seAplicaA(Achado achado, HashDoItem hashDoItem) {
        return chave.equals(ChaveDeTratativa.de(hashDoItem, achado));
    }

    public HashDoItem hashDoItem() {
        return chave.hashDoItem();
    }

    public String regraId() {
        return chave.regraId();
    }

    public String regraVersao() {
        return chave.regraVersao();
    }
}
