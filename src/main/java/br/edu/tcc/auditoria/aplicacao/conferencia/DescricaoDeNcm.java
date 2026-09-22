package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;

/**
 * A descrição que o catálogo dá ao NCM declarado, na data do documento.
 *
 * <p>Existe para ficar ao lado da descrição do produto na nota, e a comparação
 * entre as duas é o motivo de a tela mostrar as duas. Quando o que o emitente
 * escreveu e o que a tabela descreve não se parecem, o sinal é de classificação
 * errada — informação que nenhuma das duas sozinha dá, e que não é apontamento de
 * regra nenhuma: é leitura de quem responde pelo fiscal.</p>
 *
 * <p>O texto é o da carga, repetido sem edição.</p>
 */
public record DescricaoDeNcm(String ncm, String descricao, ReferenciaNormativa referencia) {

    public DescricaoDeNcm {
        if (ncm == null || ncm.isBlank()) {
            throw new ConferenciaInvalida("A descrição precisa dizer de qual NCM ela é.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new ConferenciaInvalida(
                    "O registro de NCM do catálogo sempre traz descrição; vazia aqui seria dado perdido "
                            + "no caminho.");
        }
        if (referencia == null) {
            throw new ConferenciaInvalida("A descrição do NCM precisa da vigência e da fonte.");
        }
    }

    public static DescricaoDeNcm de(RegistroNcm registro) {
        if (registro == null) {
            throw new ConferenciaInvalida("Não há registro de NCM a apresentar.");
        }
        return new DescricaoDeNcm(
                registro.ncm().valor(),
                registro.descricao(),
                ReferenciaNormativa.de(registro.procedencia()));
    }
}
