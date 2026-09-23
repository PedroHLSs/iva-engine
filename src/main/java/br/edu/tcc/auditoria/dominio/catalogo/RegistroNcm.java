package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

// Representa a existência e a descrição de um NCM numa vigência, para responder se o NCM existia na data do documento.
public record RegistroNcm(Ncm ncm, String descricao, ProcedenciaNormativa procedencia)
        implements RegistroNormativo {

    // Valida que o registro tenha NCM, descrição e procedência.
    public RegistroNcm {
        if (ncm == null) {
            throw new RegistroNormativoInvalido("O registro de NCM precisa de NCM.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "O registro do NCM \"%s\" precisa de descrição.".formatted(ncm.valor()));
        }
        if (procedencia == null) {
            throw new RegistroNormativoInvalido(
                    "O registro do NCM \"%s\" precisa de vigência e fonte normativa.".formatted(ncm.valor()));
        }
    }

    // Retorna a chave da série de vigência: o próprio NCM.
    @Override
    public String chaveDeVigencia() {
        return ncm.valor();
    }
}
