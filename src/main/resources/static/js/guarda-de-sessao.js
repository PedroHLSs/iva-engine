import { el, trocar } from './dom.js';
import { ajustarAoPerfil, exigirSessao, sair } from './sessao.js';

const usuario = await exigirSessao();
if (usuario) {
  const caixa = document.getElementById('quem-esta');
  if (caixa) {
    trocar(caixa, [
      el('span', { texto: usuario.nome + ' · ' + usuario.rotuloDoPerfil }),
      el('button', {
        type: 'button', classe: 'botao', texto: 'Sair',
        aoClicar: async () => {
          await sair();
          window.location.href = 'index.html#/entrar';
        },
      }),
    ]);
  }
  ajustarAoPerfil(usuario);
}
