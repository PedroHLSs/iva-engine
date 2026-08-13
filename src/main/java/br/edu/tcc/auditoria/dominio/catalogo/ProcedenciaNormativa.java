package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Quando um registro do catálogo vale e de onde ele veio.
 *
 * <p>Os três dados que todo registro normativo carrega obrigatoriamente —
 * início de vigência, fim de vigência e fonte normativa — ficam reunidos aqui,
 * e não repetidos soltos em cada tipo de registro. Assim é impossível criar um
 * registro novo e esquecer de datá-lo ou de dizer de onde ele saiu.</p>
 *
 * <p>A fonte normativa é texto livre informado na carga. O domínio não conhece
 * nenhuma fonte, não valida formato de citação e não tem fonte padrão.</p>
 *
 * @param vigencia       período em que o registro vale; fim vazio é vigência aberta
 * @param fonteNormativa identificação do documento de onde o registro foi extraído
 */
public record ProcedenciaNormativa(PeriodoVigencia vigencia, String fonteNormativa) {

    public ProcedenciaNormativa {
        if (vigencia == null) {
            throw new RegistroNormativoInvalido(
                    "Todo registro do catálogo precisa de vigência: sem ela não há como resolvê-lo numa data.");
        }
        if (fonteNormativa == null || fonteNormativa.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "Todo registro do catálogo precisa declarar a fonte normativa: "
                            + "sem ela o apontamento gerado não é conferível.");
        }
    }

    /** Vigência fechada nos dois extremos. */
    public static ProcedenciaNormativa de(LocalDate vigenciaInicio, LocalDate vigenciaFim, String fonteNormativa) {
        return new ProcedenciaNormativa(PeriodoVigencia.de(vigenciaInicio, vigenciaFim), fonteNormativa);
    }

    /** Vigência ainda aberta, sem último dia conhecido. */
    public static ProcedenciaNormativa aPartirDe(LocalDate vigenciaInicio, String fonteNormativa) {
        return new ProcedenciaNormativa(PeriodoVigencia.aPartirDe(vigenciaInicio), fonteNormativa);
    }

    public LocalDate vigenciaInicio() {
        return vigencia.inicio();
    }

    public Optional<LocalDate> vigenciaFim() {
        return vigencia.fim();
    }

    public boolean vigenteEm(LocalDate data) {
        return vigencia.contem(data);
    }
}
