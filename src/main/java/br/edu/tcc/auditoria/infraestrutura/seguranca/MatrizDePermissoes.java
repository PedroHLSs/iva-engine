package br.edu.tcc.auditoria.infraestrutura.seguranca;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;

import org.springframework.http.HttpMethod;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Classe com a matriz de permissão da API: para cada método e caminho, quais perfis podem chamar. É lida pelo filtro de segurança, antes de qualquer controlador; o que não está aqui é negado a todos. Esconder botão na tela não entra nesta conta.
public final class MatrizDePermissoes {

    // Representa uma linha da matriz: método, caminho e perfis que podem chamar.
    public record Permissao(HttpMethod metodo, String caminho, Set<Perfil> perfis) {

        // Guarda uma cópia imutável dos perfis.
        public Permissao {
            perfis = Set.copyOf(perfis);
        }
    }

    // Caminhos abertos sem sessão: entrar e pegar o token contra falsificação de pedido.
    public static final List<Permissao> SEM_SESSAO = List.of(
            new Permissao(HttpMethod.POST, "/api/sessao", Set.of()),
            new Permissao(HttpMethod.GET, "/api/sessao/csrf", Set.of()));

    private static final Set<Perfil> TODOS = EnumSet.allOf(Perfil.class);
    private static final Set<Perfil> QUEM_ENVIA_E_TRATA = EnumSet.of(Perfil.ADMINISTRADOR, Perfil.FISCAL);
    private static final Set<Perfil> SO_ADMINISTRADOR = EnumSet.of(Perfil.ADMINISTRADOR);

    // A matriz inteira. Execução e achado não têm PUT nem DELETE para ninguém: são registro de auditoria, e a tratativa é o único caminho de intervenção humana.
    public static final List<Permissao> COM_SESSAO = List.of(
            // A própria sessão.
            new Permissao(HttpMethod.GET, "/api/sessao", TODOS),
            new Permissao(HttpMethod.DELETE, "/api/sessao", TODOS),
            new Permissao(HttpMethod.PUT, "/api/sessao/senha", TODOS),

            // Leitura do que foi auditado.
            new Permissao(HttpMethod.GET, "/api/execucoes", TODOS),
            new Permissao(HttpMethod.GET, "/api/execucoes/{id}", TODOS),
            new Permissao(HttpMethod.GET, "/api/execucoes/{id}/achados", TODOS),
            new Permissao(HttpMethod.GET, "/api/execucoes/{id}/nao-avaliados", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}/produtos", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}/produtos/{endereco}", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}/grupos", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}/grupos/produtos", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}/vinculos", TODOS),
            new Permissao(HttpMethod.GET, "/api/analises/{id}/autoria", TODOS),
            new Permissao(HttpMethod.GET, "/api/base-tributaria", TODOS),
            new Permissao(HttpMethod.GET, "/api/achados/{id}/tratativas", TODOS),
            new Permissao(HttpMethod.GET, "/api/cargas", TODOS),
            new Permissao(HttpMethod.GET, "/api/cargas/{versao}", TODOS),

            // Acurácia (Etapa 13): ver a prévia é leitura; medir sela a carga, e por isso não é de consulta.
            new Permissao(HttpMethod.GET, "/api/acuracia/previa", TODOS),
            new Permissao(HttpMethod.POST, "/api/acuracia", QUEM_ENVIA_E_TRATA),

            // Enviar nota e tratar achado.
            new Permissao(HttpMethod.POST, "/api/analises", QUEM_ENVIA_E_TRATA),
            new Permissao(HttpMethod.POST, "/api/analises/{id}/correcoes", QUEM_ENVIA_E_TRATA),
            new Permissao(HttpMethod.POST, "/api/achados/{id}/tratativas", QUEM_ENVIA_E_TRATA),

            // Cargas de catálogo: importar, editar e excluir.
            new Permissao(HttpMethod.POST, "/api/cargas", SO_ADMINISTRADOR),
            new Permissao(HttpMethod.PUT, "/api/cargas/{versao}", SO_ADMINISTRADOR),
            new Permissao(HttpMethod.DELETE, "/api/cargas/{versao}", SO_ADMINISTRADOR),

            // Usuários.
            new Permissao(HttpMethod.GET, "/api/usuarios", SO_ADMINISTRADOR),
            new Permissao(HttpMethod.POST, "/api/usuarios", SO_ADMINISTRADOR),
            new Permissao(HttpMethod.GET, "/api/usuarios/{id}", SO_ADMINISTRADOR),
            new Permissao(HttpMethod.PUT, "/api/usuarios/{id}", SO_ADMINISTRADOR),
            new Permissao(HttpMethod.DELETE, "/api/usuarios/{id}", SO_ADMINISTRADOR));

    // Construtor privado: ninguém cria objeto desta classe, só usa as listas.
    private MatrizDePermissoes() {
    }
}
