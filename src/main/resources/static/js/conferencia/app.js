/* ---------------------------------------------------------------------------
   Ponto de entrada da interface de conferencia.

   Um modulo carregado por index.html; as telas sao modulos irmaos. Sem
   empacotador, sem npm, sem CDN, sem etapa de build: o navegador resolve os
   imports sozinho, e o Spring serve os arquivos como estao em
   src/main/resources/static. Funciona sem internet.

   A interface tecnica da Etapa 9 continua inteira, em tecnica.html, com os
   modulos dela intocados.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import { rotaAtual, aoTrocarDeRota, enderecoDeEnvio } from './roteador.js';
import { falha } from './pecas.js';

import * as telaDeEnvio from './telas/enviar.js';
import * as telaDeResultado from './telas/resultado.js';
import * as telaDeProduto from './telas/produto.js';
import * as telaDeGrupo from './telas/grupo.js';
import * as telaDaBase from './telas/base.js';
import * as telaDeHistorico from './telas/historico.js';

const TELAS = {
  enviar: telaDeEnvio,
  resultado: telaDeResultado,
  produto: telaDeProduto,
  grupo: telaDeGrupo,
  base: telaDaBase,
  historico: telaDeHistorico,
};

/** Qual item do menu corresponde a cada rota. */
const MENU = {
  enviar: 'enviar',
  resultado: 'enviar',
  produto: 'enviar',
  grupo: 'enviar',
  base: 'base',
  historico: 'historico',
};

function marcarNavegacao(nome) {
  const atual = MENU[nome] || 'enviar';
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
  marcarNavegacao(rota.nome);
  window.scrollTo(0, 0);

  const modulo = TELAS[rota.nome];
  if (!modulo) {
    trocar(tela, [
      el('h1', { texto: 'Endereco desconhecido' }),
      el('p', {}, [
        'Nao ha tela para "' + (rota.caminho || '') + '". ',
        el('a', { href: enderecoDeEnvio(), texto: 'Voltar ao inicio.' }),
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
