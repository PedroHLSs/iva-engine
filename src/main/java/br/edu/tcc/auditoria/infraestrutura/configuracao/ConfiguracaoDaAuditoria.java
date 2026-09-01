package br.edu.tcc.auditoria.infraestrutura.configuracao;

import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.RepositorioDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.RepositorioDeCargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ExportadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MontadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ServicoDeExportacao;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.dominio.tratativa.RepositorioTratativa;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhasDeLeituraEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorDocumentoFiscal;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorLote;
import br.edu.tcc.auditoria.infraestrutura.xml.NormalizadorDocumento;
import br.edu.tcc.auditoria.infraestrutura.xml.Pseudonimizador;
import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Montagem dos objetos das camadas de aplicação e domínio.
 *
 * <p>Nenhuma classe de {@code aplicacao} ou {@code dominio} tem anotação de
 * Spring, e nenhuma vai ter (D001). A ligação entre elas é feita aqui, à mão. O
 * arquivo é a fotografia de quem depende de quem — que é justamente o que a
 * injeção por anotação deixa implícito.</p>
 */
@Configuration
public class ConfiguracaoDaAuditoria {

    /**
     * Nome da propriedade que carrega o sal de pseudonimização.
     *
     * <p>Repetido aqui porque a constante equivalente é interna ao pacote
     * {@code infraestrutura.xml}. Serve para que o valor também possa vir de
     * {@code application.properties} ou de argumento de linha de comando, além
     * da propriedade de sistema e da variável de ambiente que
     * {@link SalDeInstalacao} já lê sozinho.</p>
     */
    static final String PROPRIEDADE_DO_SAL = "auditoria.pseudonimizacao.sal";

    /** Nome da propriedade que carrega a tolerância de valor da regra R05. */
    static final String PROPRIEDADE_DA_TOLERANCIA = "auditoria.tolerancia-de-valor";

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /**
     * Sal de pseudonimização.
     *
     * <p><strong>Não há valor padrão, e não pode haver.</strong> Sal conhecido
     * torna o pseudônimo reversível por força bruta — são poucos bilhões de CNPJ
     * possíveis. Sem o sal configurado, o sistema para na subida com a
     * explicação de como configurá-lo, em vez de processar com um valor fraco.</p>
     */
    @Bean
    SalDeInstalacao salDeInstalacao(Environment ambiente) {
        String configurado = ambiente.getProperty(PROPRIEDADE_DO_SAL);
        if (configurado != null && !configurado.isBlank()) {
            return new SalDeInstalacao(configurado);
        }
        // Sem valor no ambiente do Spring, cai na leitura própria da etapa 4, que
        // traz a mensagem completa de como configurar.
        return SalDeInstalacao.daConfiguracaoExterna();
    }

    /**
     * Tolerância usada pela regra que confere valor de tributo contra base e
     * alíquota.
     *
     * <p><strong>Também sem valor padrão.</strong> Tolerância não é conteúdo
     * normativo — é uma escolha de quem audita sobre quanta diferença de
     * arredondamento não merece apontamento. Escolher por conta própria seria
     * decidir, em nome do usuário, quantos centavos de divergência ficam
     * invisíveis no relatório. Para exigir igualdade exata, configure zero.</p>
     */
    @Bean
    ToleranciaDeValor toleranciaDeValor(Environment ambiente) {
        String configurada = ambiente.getProperty(PROPRIEDADE_DA_TOLERANCIA);
        if (configurada == null || configurada.isBlank()) {
            throw new ConfiguracaoInvalida(
                    ("Não há tolerância de valor configurada. Defina \"%s\" com a diferença máxima que "
                            + "não deve virar apontamento — por exemplo \"0.01\" para um centavo, ou "
                            + "\"0\" para exigir igualdade exata. O sistema não escolhe por você: essa "
                            + "escolha decide quanta divergência some do relatório.")
                            .formatted(PROPRIEDADE_DA_TOLERANCIA));
        }
        try {
            return ToleranciaDeValor.de(new BigDecimal(configurada.strip()));
        } catch (NumberFormatException naoENumero) {
            throw new ConfiguracaoInvalida(
                    "A propriedade \"%s\" não é um número: \"%s\".".formatted(
                            PROPRIEDADE_DA_TOLERANCIA, configurada),
                    naoENumero);
        }
    }

    @Bean
    Pseudonimizador pseudonimizador(SalDeInstalacao sal) {
        return new Pseudonimizador(sal);
    }

    @Bean
    LeitorDocumentoFiscal leitorDocumentoFiscal() {
        return new LeitorDocumentoFiscal();
    }

    @Bean
    NormalizadorDocumento normalizadorDocumento(Pseudonimizador pseudonimizador) {
        return new NormalizadorDocumento(pseudonimizador);
    }

    @Bean
    FalhasDeLeituraEmMemoria falhasDeLeitura() {
        return new FalhasDeLeituraEmMemoria();
    }

    @Bean
    LeitorLote leitorLote(
            LeitorDocumentoFiscal leitor,
            NormalizadorDocumento normalizador,
            FalhasDeLeituraEmMemoria falhas) {
        return new LeitorLote(leitor, normalizador, falhas);
    }

    @Bean
    MotorAuditoria motorAuditoria() {
        return new MotorAuditoria();
    }

    @Bean
    ServicoDeAuditoria servicoDeAuditoria(
            FonteDeLoteDeDocumentos fonte,
            ProvedorDeCatalogo provedorDeCatalogo,
            RepositorioDaAuditoria repositorio,
            MotorAuditoria motor,
            ToleranciaDeValor tolerancia,
            Clock relogio) {
        return new ServicoDeAuditoria(fonte, provedorDeCatalogo, repositorio, motor, tolerancia, relogio);
    }

    @Bean
    ServicoDeImportacaoDeCatalogo servicoDeImportacaoDeCatalogo(
            RepositorioDeCargaDeCatalogo repositorio) {
        return new ServicoDeImportacaoDeCatalogo(repositorio);
    }

    @Bean
    ServicoDeTratativa servicoDeTratativa(
            ConsultaDeAchados consulta, RepositorioTratativa repositorio, Clock relogio) {
        return new ServicoDeTratativa(consulta, repositorio, relogio);
    }

    @Bean
    MontadorDePapelDeTrabalho montadorDePapelDeTrabalho(
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador) {
        return new MontadorDePapelDeTrabalho(achados, naoAvaliadas, documentos, pseudonimizador);
    }

    @Bean
    ServicoDeExportacao servicoDeExportacao(
            ConsultaDeExecucoes execucoes,
            MontadorDePapelDeTrabalho montador,
            ExportadorDePapelDeTrabalho exportador) {
        return new ServicoDeExportacao(execucoes, montador, exportador);
    }
}
