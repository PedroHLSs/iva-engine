package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.infraestrutura.sal.ImpressaoDigitalDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.OrigemDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/** A impressão digital do sal, gravada em banco. */
@Component
public class RegistroDaImpressaoDigitalNoBanco implements RegistroDaImpressaoDigital {

    private final ImpressaoDigitalDoSalJpa impressoes;
    private final Clock relogio;

    RegistroDaImpressaoDigitalNoBanco(ImpressaoDigitalDoSalJpa impressoes, Clock relogio) {
        this.impressoes = impressoes;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registro> registrada() {
        return impressoes.findById(ImpressaoDigitalDoSalEntidade.LINHA_UNICA)
                .map(entidade -> new Registro(
                        new ImpressaoDigitalDoSal(entidade.valor()),
                        origemDe(entidade.origem()),
                        entidade.adotadaDeAcervoExistente()));
    }

    @Override
    @Transactional
    public void registrar(
            ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobreAcervoExistente) {

        impressoes.save(new ImpressaoDigitalDoSalEntidade(
                impressao.valor(), relogio.instant(), origem.name(), sobreAcervoExistente));
    }

    /**
     * Traduz o nome gravado de volta para a origem.
     *
     * <p>Nome desconhecido não derruba a leitura: a origem é informativa, não
     * participa da comparação de impressão digital, e recusar a subida porque uma
     * versão anterior gravou um nome que esta não conhece seria travar por um
     * campo que não decide nada. O diagnóstico dirá o que está gravado.</p>
     */
    private static OrigemDoSal origemDe(String gravada) {
        for (OrigemDoSal origem : OrigemDoSal.values()) {
            if (origem.name().equals(gravada)) {
                return origem;
            }
        }
        return OrigemDoSal.PROPRIEDADE_DE_CONFIGURACAO;
    }
}
