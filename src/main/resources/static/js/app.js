import { el, trocar } from './dom.js';
import { rotaAtual, aoTrocarDeRota, endereco } from './roteador.js';
import { falha } from './comum.js';

import * as telaDeExecucoes from './telas/execucoes.js';
import * as telaDePanorama from './telas/panorama.js';
import * as telaDeAchados from './telas/achados.js';
import * as telaDeAchado from './telas/achado.js';
import * as telaDeNaoAvaliados from './telas/naoavaliados.js';
import * as telaDeAcuracia from './telas/acuracia.js';

const TELAS = {
  execucoes: telaDeExecucoes,
  panorama: telaDePanorama,
  achados: telaDeAchados,
  achado: telaDeAchado,
  naoAvaliados: telaDeNaoAvaliados,
  acuracia: telaDeAcuracia,
};

function marcarNavegacao(nome) {
  const atual = nome === 'acuracia' ? 'acuracia' : 'execucoes';
  for (const atalho of document.querySelectorAll('.navegacao a')) {
    if (atalho.dataset.rota === atual) {
      atalho.setAttribute('aria-current', 'page');
    } else {
      atalho.removeAttribute('aria-current');
    }
  }
}

async function desenhar() {
  const tela = document.getElementById('tela');
  const rota = rotaAtual();
  document.body.classList.toggle('visual-painel', ['execucoes', 'panorama', 'acuracia'].includes(rota.nome));
  marcarNavegacao(rota.nome);
  window.scrollTo(0, 0);

  const modulo = TELAS[rota.nome];
  if (!modulo) {
    trocar(tela, [
      el('h1', { texto: 'Endereco desconhecido' }),
      el('p', {}, [
        'Nao ha tela para "' + (rota.caminho || '') + '". ',
        el('a', { href: endereco('execucoes'), texto: 'Voltar para as execucoes.' }),
      ]),
    ]);
    return;
  }

  try {
    await modulo.desenhar(tela, rota.parametros, rota);
  } catch (erro) {
    trocar(tela, [falha(erro)]);
    throw erro;
  }
}

aoTrocarDeRota(() => {
  desenhar();
});
