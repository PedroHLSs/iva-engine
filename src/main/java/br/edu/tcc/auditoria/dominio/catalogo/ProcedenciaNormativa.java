package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.time.LocalDate;
import java.util.Optional;

// Representa quando um registro do catálogo vale e de onde ele veio; a fonte é texto livre da carga, sem fonte padrão.
public record ProcedenciaNormativa(PeriodoVigencia vigencia, String fonteNormativa) {

    // Valida que a procedência tenha vigência e fonte normativa.
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

    // Método estático que cria a procedência com vigência fechada nos dois extremos.
    public static ProcedenciaNormativa de(LocalDate vigenciaInicio, LocalDate vigenciaFim, String fonteNormativa) {
        return new ProcedenciaNormativa(PeriodoVigencia.de(vigenciaInicio, vigenciaFim), fonteNormativa);
    }

    // Método estático que cria a procedência com vigência aberta, sem último dia conhecido.
    public static ProcedenciaNormativa aPartirDe(LocalDate vigenciaInicio, String fonteNormativa) {
        return new ProcedenciaNormativa(PeriodoVigencia.aPartirDe(vigenciaInicio), fonteNormativa);
    }

    public LocalDate vigenciaInicio() {
        return vigencia.inicio();
    }

    public Optional<LocalDate> vigenciaFim() {
        return vigencia.fim();
    }

    // Indica se o registro vale na data indicada.
    public boolean vigenteEm(LocalDate data) {
        return vigencia.contem(data);
    }
}
