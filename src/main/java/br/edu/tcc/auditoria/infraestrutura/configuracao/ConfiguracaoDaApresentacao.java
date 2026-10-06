package br.edu.tcc.auditoria.infraestrutura.configuracao;

import br.edu.tcc.auditoria.aplicacao.conferencia.MontadorDaConferencia;
import br.edu.tcc.auditoria.aplicacao.historico.AcervoDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.ServicoDoHistorico;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Classe de configuração do Spring que monta os serviços da Etapa 13, separada das outras para a etapa não mexer nelas.
@Configuration
public class ConfiguracaoDaApresentacao {

    // Cria o serviço do histórico de análises.
    @Bean
    ServicoDoHistorico servicoDoHistorico(AcervoDoHistorico acervo, MontadorDaConferencia montador, Clock relogio) {
        return new ServicoDoHistorico(acervo, montador, relogio);
    }
}
