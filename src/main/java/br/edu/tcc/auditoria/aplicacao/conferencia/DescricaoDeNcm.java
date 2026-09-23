package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;

// Representa a descrição que o catálogo dá ao NCM declarado, para ficar ao lado da descrição do produto na nota.
public record DescricaoDeNcm(String ncm, String descricao, ReferenciaNormativa referencia) {

    // Valida que a descrição tenha NCM, texto e referência normativa.
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

    // Método estático que cria a descrição de exibição a partir do registro de NCM do catálogo.
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
