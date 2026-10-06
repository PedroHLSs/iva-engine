package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Representa o que a carga diz sobre o cClassTrib declarado; redução não declarada fica Optional vazio, nunca zero.
// Emenda de 03/10/2026 (D015): os campos exigidos passaram a Optional, como no domínio — vazio é "a carga não declarou", lista vazia é NENHUM. Antes os dois chegavam aqui como a mesma lista vazia.
public record ClassificacaoDoCatalogo(
        String codigo,
        List<String> cstsAdmitidos,
        String dispositivoLegal,
        boolean indicadorDeBeneficio,
        Optional<BigDecimal> percentualReducao,
        Optional<List<String>> camposObrigatoriosCondicionados,
        ReferenciaNormativa referencia) {

    // Valida a classificação: exige código, dispositivo e referência, e recusa redução nula.
    public ClassificacaoDoCatalogo {
        if (codigo == null || codigo.isBlank()) {
            throw new ConferenciaInvalida("A classificação precisa do código a que se refere.");
        }
        if (dispositivoLegal == null || dispositivoLegal.isBlank()) {
            throw new ConferenciaInvalida(
                    "A classificação do catálogo sempre cita dispositivo; vazio aqui seria dado perdido "
                            + "no caminho.");
        }
        if (cstsAdmitidos == null || camposObrigatoriosCondicionados == null) {
            throw new ConferenciaInvalida(
                    "A lista de CST deve ser vazia quando não há nenhum, e os campos condicionados "
                            + "Optional.empty() quando a carga não os declara; nunca nulos.");
        }
        if (percentualReducao == null) {
            throw new ConferenciaInvalida(
                    "Redução não declarada se representa com Optional.empty(), nunca com nulo e nunca "
                            + "com zero.");
        }
        if (referencia == null) {
            throw new ConferenciaInvalida("A classificação precisa da vigência e da fonte.");
        }
        cstsAdmitidos = List.copyOf(cstsAdmitidos);
        camposObrigatoriosCondicionados = camposObrigatoriosCondicionados.map(List::copyOf);
    }

    // Método estático que cria a classificação de exibição a partir do registro do catálogo.
    public static ClassificacaoDoCatalogo de(ClassificacaoTributaria registro) {
        if (registro == null) {
            throw new ConferenciaInvalida("Não há classificação tributária a apresentar.");
        }
        return new ClassificacaoDoCatalogo(
                registro.codigo().valor(),
                // Ordena os CSTs para que duas aberturas da mesma nota listem na mesma ordem.
                registro.cstsCompativeis().stream()
                        .map(CodigoCst::valor)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                registro.dispositivoLegal(),
                registro.indicadorDeBeneficio(),
                registro.percentualReducao(),
                registro.camposObrigatoriosCondicionados(),
                ReferenciaNormativa.de(registro.procedencia()));
    }

    // Indica se a carga admite este CST junto deste código, na vigência apresentada.
    public boolean admite(String cst) {
        return cst != null && cstsAdmitidos.contains(cst);
    }
}
