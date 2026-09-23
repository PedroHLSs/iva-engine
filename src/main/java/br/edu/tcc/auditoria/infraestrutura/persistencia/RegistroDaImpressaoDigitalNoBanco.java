package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.infraestrutura.sal.ImpressaoDigitalDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.OrigemDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

// Classe que grava e lê do banco a impressão digital do sal.
@Component
public class RegistroDaImpressaoDigitalNoBanco implements RegistroDaImpressaoDigital {

    private final ImpressaoDigitalDoSalJpa impressoes;
    private final Clock relogio;

    // Construtor que recebe o repositório da impressão digital e o relógio.
    RegistroDaImpressaoDigitalNoBanco(ImpressaoDigitalDoSalJpa impressoes, Clock relogio) {
        this.impressoes = impressoes;
        this.relogio = relogio;
    }

    // Busca a impressão digital gravada, se houver.
    @Override
    @Transactional(readOnly = true)
    public Optional<Registro> registrada() {
        return impressoes.findById(ImpressaoDigitalDoSalEntidade.LINHA_UNICA)
                .map(entidade -> new Registro(
                        new ImpressaoDigitalDoSal(entidade.valor()),
                        origemDe(entidade.origem()),
                        entidade.adotadaDeAcervoExistente()));
    }

    // Grava a impressão digital, substituindo a anterior, porque a tabela tem uma linha só.
    @Override
    @Transactional
    public void registrar(
            ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobreAcervoExistente) {

        impressoes.save(new ImpressaoDigitalDoSalEntidade(
                impressao.valor(), relogio.instant(), origem.name(), sobreAcervoExistente));
    }

    // Método auxiliar que converte o nome gravado na origem do sal; nome desconhecido vira PROPRIEDADE_DE_CONFIGURACAO, em vez de travar a leitura, porque a origem só informa e não decide nada.
    private static OrigemDoSal origemDe(String gravada) {
        for (OrigemDoSal origem : OrigemDoSal.values()) {
            if (origem.name().equals(gravada)) {
                return origem;
            }
        }
        return OrigemDoSal.PROPRIEDADE_DE_CONFIGURACAO;
    }
}
