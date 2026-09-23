package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;

// Representa um vínculo entre o NCM declarado e um anexo, vigente na data do documento; a tela mostra todos, sem escolher.
public record EnquadramentoDoNcm(
        String ncm, String anexo, String tipoDeTratamento, ReferenciaNormativa referencia) {

    // Valida que o enquadramento tenha NCM, anexo, tipo de tratamento e referência normativa.
    public EnquadramentoDoNcm {
        if (ncm == null || ncm.isBlank()) {
            throw new ConferenciaInvalida("O enquadramento precisa dizer de qual NCM ele é.");
        }
        if (anexo == null || anexo.isBlank()) {
            throw new ConferenciaInvalida("O enquadramento precisa identificar o anexo.");
        }
        if (tipoDeTratamento == null || tipoDeTratamento.isBlank()) {
            throw new ConferenciaInvalida("O enquadramento precisa do tipo de tratamento da carga.");
        }
        if (referencia == null) {
            throw new ConferenciaInvalida("O enquadramento precisa da vigência e da fonte.");
        }
    }

    // Método estático que cria o enquadramento de exibição a partir do item de anexo do catálogo.
    public static EnquadramentoDoNcm de(ItemAnexo item) {
        if (item == null) {
            throw new ConferenciaInvalida("Não há item de anexo a apresentar.");
        }
        return new EnquadramentoDoNcm(
                item.ncm().valor(),
                item.identificadorDoAnexo().valor(),
                item.tipoDeTratamento(),
                ReferenciaNormativa.de(item.procedencia()));
    }
}
