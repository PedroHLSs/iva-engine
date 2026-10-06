package br.edu.tcc.auditoria.infraestrutura.seguranca;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.RepositorioDeUsuarios;
import br.edu.tcc.auditoria.infraestrutura.seguranca.MatrizDePermissoes.Permissao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

// Classe de configuração da segurança da API: sessão no servidor, token contra falsificação de pedido em toda escrita, e a matriz de permissão aplicada no filtro, antes de qualquer controlador. Só existe com o servidor web; a linha de comando não passa por aqui. Acrescentada na Etapa 12.
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ConfiguracaoDeSeguranca {

    // Monta a cadeia de filtros: token em toda escrita, a matriz de permissão, e negação para qualquer caminho da API que não esteja nela.
    @Bean
    SecurityFilterChain cadeiaDeSeguranca(
            HttpSecurity http,
            RepositorioDeUsuarios usuarios,
            SecurityContextRepository contextos) throws Exception {

        RespostaDeRecusa recusa = new RespostaDeRecusa();

        http
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(recusa)
                        .accessDeniedHandler(recusa))
                .addFilterBefore(new RecargaDoUsuario(usuarios, contextos, recusa),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(pedidos -> {
                    for (Permissao aberta : MatrizDePermissoes.SEM_SESSAO) {
                        pedidos.requestMatchers(aberta.metodo(), aberta.caminho()).permitAll();
                    }
                    for (Permissao permissao : MatrizDePermissoes.COM_SESSAO) {
                        pedidos.requestMatchers(permissao.metodo(), permissao.caminho())
                                .hasAnyRole(permissao.perfis().stream().map(Perfil::name).toArray(String[]::new));
                    }
                    // Qualquer outro caminho da API é negado, inclusive PUT e DELETE sobre execução e achado.
                    pedidos.requestMatchers("/api/**").denyAll();
                    // As páginas estáticas não trazem dado nenhum; o dado vem da API, que exige sessão.
                    pedidos.anyRequest().permitAll();
                });
        return http.build();
    }
}
