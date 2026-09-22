package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;

/**
 * Um vínculo entre o NCM declarado e um anexo, vigente na data do documento.
 *
 * <p>É a parte da tela que responde à pergunta que dá nome à ferramenta: que
 * tratamento se aplica a este produto. O anexo e o tipo de tratamento são
 * rótulos da carga — o sistema não sabe o que cada anexo contém nem o que cada
 * tratamento implica, e por isso não escreve frase nenhuma em cima deles.</p>
 *
 * <p>Um NCM pode aparecer em mais de um anexo na mesma data, e o catálogo admite
 * isso de propósito ({@code ItemAnexo} tem chave de vigência por par NCM e
 * anexo). A tela mostra todos, sem escolher: escolher seria julgamento fiscal,
 * e é justamente ele que cabe a quem confere.</p>
 */
public record EnquadramentoDoNcm(
        String ncm, String anexo, String tipoDeTratamento, ReferenciaNormativa referencia) {

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
