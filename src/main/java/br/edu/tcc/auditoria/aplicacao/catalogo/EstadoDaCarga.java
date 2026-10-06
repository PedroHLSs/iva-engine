package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.Instant;
import java.util.Optional;

// Representa a situação de uma carga de catálogo: quando entrou, se está selada, quantas análises gravadas a usam, de qual carga veio e quantos registros tem por tabela.
public record EstadoDaCarga(
        String versao,
        Instant importadoEm,
        Optional<Instant> seladaEm,
        long analisesQueUsam,
        Optional<String> derivadaDe,
        Optional<Instant> alteradaEm,
        boolean maisRecente,
        int classificacoesTributarias,
        int registrosDeNcm,
        int itensDeAnexo,
        int aliquotas,
        NaturezaDaCarga natureza) {

    // Valida que o estado tenha versão, data, opcionais preenchidos e contagens não negativas; carga usada por análise sem selo é contradição.
    public EstadoDaCarga {
        if (versao == null || versao.isBlank() || importadoEm == null) {
            throw new CatalogoInvalido("O estado da carga precisa da versão e da data de importação.");
        }
        if (seladaEm == null || derivadaDe == null || alteradaEm == null || natureza == null) {
            throw new CatalogoInvalido(
                    "Campo ausente do estado da carga se representa com Optional.empty(), nunca com nulo.");
        }
        if (analisesQueUsam < 0 || classificacoesTributarias < 0 || registrosDeNcm < 0
                || itensDeAnexo < 0 || aliquotas < 0) {
            throw new CatalogoInvalido("Contagem negativa no estado da carga.");
        }
        if (analisesQueUsam > 0 && seladaEm.isEmpty()) {
            throw new CatalogoInvalido(
                    ("A carga \"%s\" é usada por %d análise(s) e não está selada. Isso só acontece se o "
                            + "banco foi alterado por fora.").formatted(versao, analisesQueUsam));
        }
    }

    // Diz se a carga já foi entregue a alguma análise.
    public boolean selada() {
        return seladaEm.isPresent();
    }

    // Diz o que salvar uma edição faria com esta carga.
    public EfeitoDaEdicao efeitoDaEdicao() {
        return selada() ? EfeitoDaEdicao.CRIAR_VERSAO_NOVA : EfeitoDaEdicao.ALTERAR_RASCUNHO;
    }
}
