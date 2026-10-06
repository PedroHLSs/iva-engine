package br.edu.tcc.auditoria.infraestrutura.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

// Classe de configuração com as duas peças de autenticação que o controlador de sessão usa. Fica fora da ConfiguracaoDeSeguranca porque os controladores existem também quando o sistema sobe sem servidor web, como na linha de comando, e precisam delas para serem criados.
@Configuration
class ConfiguracaoDaAutenticacao {

    // Quem confere login e senha: só a AutenticacaoPorSenha.
    @Bean
    AuthenticationManager gerenciadorDeAutenticacao(AutenticacaoPorSenha autenticacao) {
        return new ProviderManager(autenticacao);
    }

    // Onde a sessão guarda quem está logado.
    @Bean
    SecurityContextRepository repositorioDoContexto() {
        return new HttpSessionSecurityContextRepository();
    }
}
