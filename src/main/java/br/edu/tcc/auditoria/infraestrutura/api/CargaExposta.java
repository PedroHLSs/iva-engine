package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.PreviaDaEdicao;

import java.time.Instant;

// Representa uma carga de catálogo como a API mostra, com o que salvar uma edição faria e se ela pode ser excluída, ditos antes de a pessoa confirmar. Todo campo ausente vem null com o motivo ao lado.
public record CargaExposta(
        String versao,
        Instant importadoEm,
        boolean selada,
        Instant seladaEm,
        String motivoDoSeloAusente,
        long analisesQueUsam,
        String derivadaDe,
        String motivoDaOrigemAusente,
        Instant alteradaEm,
        String motivoDaAlteracaoAusente,
        boolean maisRecente,
        ContagemExposta registros,
        FaixaDeNatureza natureza,
        PreviaExposta edicao,
        ExclusaoExposta exclusao) {

    // Representa quantos registros a carga tem em cada tabela.
    public record ContagemExposta(int classificacoesTributarias, int registrosDeNcm, int itensDeAnexo, int aliquotas) {
    }

    // Representa o que salvar uma edição vai fazer. A versão a criar só existe quando a edição cria versão nova, e vem null com o motivo quando não.
    public record PreviaExposta(
            String efeito,
            long analisesQueUsam,
            String versaoQueSeraCriada,
            String motivoDaVersaoAusente,
            boolean passaraASerAMaisRecente,
            String aviso) {

        // Método estático que monta a prévia exposta.
        static PreviaExposta de(PreviaDaEdicao previa) {
            return new PreviaExposta(
                    previa.efeito().name(),
                    previa.analisesQueUsam(),
                    previa.versaoQueSeraCriada().orElse(null),
                    previa.versaoQueSeraCriada().isPresent()
                            ? null
                            : "a carga é rascunho: salvar altera a própria carga, sem criar versão nova",
                    previa.passaraASerAMaisRecente(),
                    previa.aviso());
        }
    }

    // Representa se a carga pode ser excluída e, se não pode, por quê.
    public record ExclusaoExposta(boolean permitida, String motivo) {
    }

    // Método estático que monta a carga exposta a partir do estado, da prévia e do motivo de não poder excluir.
    static CargaExposta de(EstadoDaCarga estado, PreviaDaEdicao previa, String motivoDeNaoExcluir) {
        return new CargaExposta(
                estado.versao(),
                estado.importadoEm(),
                estado.selada(),
                estado.seladaEm().orElse(null),
                estado.selada() ? null : "rascunho: a carga ainda não foi entregue a nenhuma análise",
                estado.analisesQueUsam(),
                estado.derivadaDe().orElse(null),
                estado.derivadaDe().isPresent() ? null : "carga importada, e não criada pela edição de outra",
                estado.alteradaEm().orElse(null),
                estado.alteradaEm().isPresent() ? null : "o conteúdo nunca foi alterado depois de importado",
                estado.maisRecente(),
                new ContagemExposta(estado.classificacoesTributarias(), estado.registrosDeNcm(),
                        estado.itensDeAnexo(), estado.aliquotas()),
                FaixaDeNatureza.de(estado.natureza(), estado.versao()),
                PreviaExposta.de(previa),
                new ExclusaoExposta(motivoDeNaoExcluir == null,
                        motivoDeNaoExcluir == null
                                ? "rascunho: nenhuma análise usou esta carga, e ela pode ser excluída"
                                : motivoDeNaoExcluir));
    }
}
