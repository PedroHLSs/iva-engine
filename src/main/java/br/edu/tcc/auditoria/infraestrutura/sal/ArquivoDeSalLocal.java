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

// Classe que guarda o sal num arquivo local, fora do repositório: em %APPDATA% no Windows e em $XDG_CONFIG_HOME ou ~/.config nos outros sistemas. Dentro do repositório ele acabaria versionado, e sal público deixa descobrir o CNPJ por força bruta.
public final class ArquivoDeSalLocal {

    static final String PASTA = "auditoria-ibs-cbs";
    static final String NOME_DO_ARQUIVO = "sal";

    private final Path caminho;

    // Construtor que recebe o caminho do arquivo.
    ArquivoDeSalLocal(Path caminho) {
        this.caminho = caminho;
    }

    // Método estático que aponta para o arquivo na pasta de configuração do sistema operacional.
    public static ArquivoDeSalLocal doSistemaOperacional() {
        return new ArquivoDeSalLocal(pastaDeConfiguracao().resolve(PASTA).resolve(NOME_DO_ARQUIVO));
    }

    // Método estático que aponta para um arquivo num caminho informado; usado em teste, para não mexer na máquina.
    public static ArquivoDeSalLocal em(Path caminho) {
        return new ArquivoDeSalLocal(caminho);
    }

    public Path caminho() {
        return caminho;
    }

    // Lê o sal gravado; devolve vazio se o arquivo não existe ou está vazio, para arquivo cortado no meio da escrita não virar sal vazio.
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

    // Grava o sal, criando a pasta e restringindo a permissão onde o sistema deixar; devolve o que conseguiu restringir, para o diagnóstico dizer a verdade.
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

    // Representa o que a restrição de permissão conseguiu fazer, e como.
    public record Restricao(boolean aplicada, String comoFoiFeita) {

        // Método estático que registra a restrição POSIX 600: só o dono lê e escreve.
        static Restricao posix() {
            return new Restricao(true, "permissão POSIX 600: só o dono lê e escreve");
        }

        // Método estático que registra a ACL do Windows reescrita só para o dono.
        static Restricao acl(String dono) {
            return new Restricao(true,
                    "ACL do Windows reescrita para conceder acesso somente a \"%s\"".formatted(dono));
        }

        // Método estático que registra que a restrição não foi aplicada, com o motivo.
        static Restricao naoAplicada(String motivo) {
            return new Restricao(false, motivo);
        }
    }

    // Método estático que devolve a pasta de configuração do usuário: %APPDATA%, ou ~/AppData/Roaming, no Windows, e $XDG_CONFIG_HOME, ou ~/.config, nos outros sistemas.
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

    // Método estático que diz se o sistema operacional é Windows.
    static boolean ehWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    // Método auxiliar que restringe a pasta ao dono, quando o sistema tem permissão POSIX.
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
            // Se não der para restringir a pasta, segue: o arquivo é restringido em seguida, e o diagnóstico diz o que foi feito.
        }
    }

    // Método auxiliar que restringe o arquivo ao dono: 600 em POSIX, ou ACL só do dono no Windows. Se nenhum dos dois der, devolve isso, em vez de dizer que o arquivo está protegido.
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
