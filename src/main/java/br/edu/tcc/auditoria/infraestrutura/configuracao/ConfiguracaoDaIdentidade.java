package br.edu.tcc.auditoria.infraestrutura.configuracao;

import br.edu.tcc.auditoria.aplicacao.catalogo.AcervoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.identidade.CodificadorDeSenha;
import br.edu.tcc.auditoria.aplicacao.identidade.RepositorioDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.tratativa.HistoricoDeTratativas;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativaAtribuida;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Classe de configuração do Spring que monta os serviços da Etapa 12: usuários, tratativa com autor e cargas de catálogo. Fica separada de ConfiguracaoDaAuditoria para a etapa não precisar mexer naquele arquivo.
@Configuration
public class ConfiguracaoDaIdentidade {

    // Cria o serviço de usuários, usado pela API e pelo comando criar-administrador.
    @Bean
    ServicoDeUsuarios servicoDeUsuarios(
            RepositorioDeUsuarios repositorio, CodificadorDeSenha codificador, Clock relogio) {
        return new ServicoDeUsuarios(repositorio, codificador, relogio);
    }

    // Cria o serviço que grava a tratativa com quem decidiu.
    @Bean
    ServicoDeTratativaAtribuida servicoDeTratativaAtribuida(
            ConsultaDeAchados consulta,
            HistoricoDeTratativas historico,
            RepositorioDeUsuarios usuarios,
            Clock relogio) {
        return new ServicoDeTratativaAtribuida(consulta, historico, usuarios, relogio);
    }

    // Cria o serviço de cargas de catálogo, que decide entre editar o rascunho e criar versão nova.
    @Bean
    ServicoDeCargas servicoDeCargas(AcervoDeCargas acervo, ServicoDeImportacaoDeCatalogo importacao) {
        return new ServicoDeCargas(acervo, importacao);
    }
}
