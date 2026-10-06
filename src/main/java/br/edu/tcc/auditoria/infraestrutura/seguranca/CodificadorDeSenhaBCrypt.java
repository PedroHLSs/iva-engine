package br.edu.tcc.auditoria.infraestrutura.seguranca;

import br.edu.tcc.auditoria.aplicacao.identidade.CodificadorDeSenha;
import br.edu.tcc.auditoria.aplicacao.identidade.HashDeSenha;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

// Classe que gera e confere hash de senha com BCrypt. Cada hash leva um sal sorteado na hora, gravado dentro do próprio texto do hash; a senha nunca é gravada. Fica fora do perfil da API porque o comando criar-administrador também precisa dela.
@Component
class CodificadorDeSenhaBCrypt implements CodificadorDeSenha {

    // Custo do BCrypt: cada ponto a mais dobra o tempo de conferir uma senha, e de tentar adivinhá-la.
    static final int CUSTO = 12;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(CUSTO);

    @Override
    public HashDeSenha codificar(SenhaInformada senha) {
        return new HashDeSenha(bcrypt.encode(senha.valor()));
    }

    @Override
    public boolean confere(SenhaInformada senha, HashDeSenha hash) {
        try {
            return bcrypt.matches(senha.valor(), hash.valor());
        } catch (IllegalArgumentException senhaGrandeDemais) {
            // Senha acima de 72 bytes não confere com nenhum hash: a recusa é a mesma de senha errada.
            return false;
        }
    }
}
