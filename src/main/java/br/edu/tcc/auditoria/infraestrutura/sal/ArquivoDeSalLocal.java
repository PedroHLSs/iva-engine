package br.edu.tcc.auditoria.infraestrutura.sal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O sal guardado em arquivo local, fora do repositório.
 *
 * <h2>Onde o arquivo fica, e por que não fica no projeto</h2>
 *
 * <p>No Windows, sob {@code %APPDATA%}; no Linux e no macOS, sob
 * {@code $XDG_CONFIG_HOME} ou {@code ~/.config}. São os lugares que cada sistema
 * reserva para configuração de usuário, e têm duas propriedades que importam
 * aqui: já são privados do usuário, e não são varridos por ferramenta de build
 * nem por sincronizador de pasta de projeto.</p>
 *
 * <p>Guardar o sal dentro do repositório seria versioná-lo no primeiro
 * {@code git add} distraído, e sal versionado é sal público — o que torna o
 * pseudônimo reversível por força bruta, que é justamente o que
 * {@code SalDeInstalacao} existe para impedir.</p>
 */
public final class ArquivoDeSalLocal {

    static final String PASTA = "auditoria-ibs-cbs";
    static final String NOME_DO_ARQUIVO = "sal";

    private final Path caminho;

    ArquivoDeSalLocal(Path caminho) {
        this.caminho = caminho;
    }

    /** O arquivo no lugar que o sistema operacional reserva para configuração. */
    public static ArquivoDeSalLocal doSistemaOperacional() {
        return new ArquivoDeSalLocal(pastaDeConfiguracao().resolve(PASTA).resolve(NOME_DO_ARQUIVO));
    }

    /** O arquivo num caminho indicado — usado em teste, para não tocar a máquina. */
    public static ArquivoDeSalLocal em(Path caminho) {
        return new ArquivoDeSalLocal(caminho);
    }

    public Path caminho() {
        return caminho;
    }

    /**
     * O sal gravado, se o arquivo existir e tiver conteúdo.
     *
     * <p>Arquivo existente e vazio devolve vazio, e não texto em branco: um
     * arquivo truncado por interrupção de escrita não deve virar um sal de zero
     * caractere, que seria recusado mais adiante com uma mensagem que não aponta
     * para a causa.</p>
     */
    public Optional<String> ler() {
        if (!Files.isRegularFile(caminho)) {
            return Optional.empty();
        }
        try {
            String conteudo = Files.readString(caminho, StandardCharsets.UTF_8).strip();
            return conteudo.isEmpty() ? Optional.empty() : Optional.of(conteudo);
        } catch (IOException erroDeLeitura) {
            throw new UncheckedIOException(
                    "Falha ao ler o sal em \"%s\".".formatted(caminho), erroDeLeitura);
        }
    }

    /**
     * Grava o sal, criando o diretório e restringindo a permissão onde o sistema
     * operacional permitir.
     *
     * @return o que se conseguiu fazer de restrição, para o diagnóstico poder
     *         dizer a verdade em vez de afirmar que o arquivo está protegido
     */
    public Restricao gravar(String sal) {
        try {
            Path pasta = caminho.getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
                restringirPasta(pasta);
            }
            Files.writeString(caminho, sal + System.lineSeparator(), StandardCharsets.UTF_8);
            return restringirArquivo(caminho);
        } catch (IOException erroDeEscrita) {
            throw new UncheckedIOException(
                    "Falha ao gravar o sal em \"%s\".".formatted(caminho), erroDeEscrita);
        }
    }

    /** O que a restrição de permissão conseguiu fazer, e como. */
    public record Restricao(boolean aplicada, String comoFoiFeita) {

        static Restricao posix() {
            return new Restricao(true, "permissão POSIX 600: só o dono lê e escreve");
        }

        static Restricao acl(String dono) {
            return new Restricao(true,
                    "ACL do Windows reescrita para conceder acesso somente a \"%s\"".formatted(dono));
        }

        static Restricao naoAplicada(String motivo) {
            return new Restricao(false, motivo);
        }
    }

    /**
     * A pasta de configuração do usuário, conforme o sistema operacional.
     *
     * <p>No Windows, {@code %APPDATA%}, com {@code ~/AppData/Roaming} como
     * segunda opção para o caso de a variável não estar definida. Fora do
     * Windows, {@code $XDG_CONFIG_HOME} quando declarado, e {@code ~/.config}
     * quando não — que é o padrão que a especificação XDG manda assumir.</p>
     */
    static Path pastaDeConfiguracao() {
        String lar = System.getProperty("user.home");
        if (ehWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData);
            }
            return Path.of(lar, "AppData", "Roaming");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg);
        }
        return Path.of(lar, ".config");
    }

    static boolean ehWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void restringirPasta(Path pasta) {
        PosixFileAttributeView posix = Files.getFileAttributeView(pasta, PosixFileAttributeView.class);
        if (posix == null) {
            return;
        }
        try {
            posix.setPermissions(EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException naoDeuParaRestringir) {
            // A pasta pode ser de outro dono, ou estar em sistema de arquivos que
            // não guarda permissão. O arquivo ainda será restringido em seguida, e
            // o diagnóstico dirá o que se conseguiu fazer.
        }
    }

    /**
     * Restringe o arquivo ao dono.
     *
     * <p>Em POSIX é 600. No Windows, a ACL herdada da pasta é substituída por uma
     * entrada única para o dono — o que remove qualquer herança que desse acesso
     * a outro grupo. Onde nenhuma das duas visões existir, a restrição não é
     * aplicada e isso é <strong>devolvido</strong>, nunca engolido: dizer que o
     * arquivo está protegido sem ter protegido seria pior do que não tentar.</p>
     */
    private static Restricao restringirArquivo(Path arquivo) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(arquivo, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> soODono =
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            posix.setPermissions(soODono);
            return Restricao.posix();
        }

        AclFileAttributeView acl = Files.getFileAttributeView(arquivo, AclFileAttributeView.class);
        if (acl == null) {
            return Restricao.naoAplicada(
                    "este sistema de arquivos não expõe permissão POSIX nem ACL; o arquivo herda a "
                            + "proteção da pasta de configuração do usuário");
        }
        try {
            UserPrincipal dono = Files.getOwner(arquivo);
            AclEntry soODono = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(dono)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            acl.setAcl(List.of(soODono));
            return Restricao.acl(dono.getName());
        } catch (IOException | SecurityException naoDeuParaRestringir) {
            return Restricao.naoAplicada(
                    "não foi possível reescrever a ACL do arquivo (%s); ele herda a proteção da pasta "
                            + "de configuração do usuário".formatted(naoDeuParaRestringir.getMessage()));
        }
    }
}
