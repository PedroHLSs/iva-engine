import { el, trocar } from '../dom.js';
import { rotaAtual, aoTrocarDeRota, enderecoDeEnvio } from './roteador.js';
import { falha } from './pecas.js';
import { ajustarAoPerfil, exigirSessao, sair } from '../sessao.js';

import * as telaDeEnvio from './telas/enviar.js';
import * as telaDeResultado from './telas/resultado.js';
import * as telaDeProduto from './telas/produto.js';
import * as telaDeGrupo from './telas/grupo.js';
import * as telaDaBase from './telas/base.js';
import * as telaDeHistorico from './telas/historico.js';
import * as telaDeEntrada from './telas/entrar.js';
import * as telaDaSenha from './telas/senha.js';
import * as telaDeUsuarios from './telas/usuarios.js';
import * as telaDasCargas from './telas/cargas.js';
import * as telaDaCarga from './telas/carga.js';
import * as telaDeAcuracia from './telas/acuracia.js';

const TELAS = {
  enviar: telaDeEnvio,
  resultado: telaDeResultado,
  produto: telaDeProduto,
  grupo: telaDeGrupo,
  base: telaDaBase,
  historico: telaDeHistorico,
  entrar: telaDeEntrada,
  senha: telaDaSenha,
  usuarios: telaDeUsuarios,
  cargas: telaDasCargas,
  carga: telaDaCarga,
  acuracia: telaDeAcuracia,
};

const MENU = {
  enviar: 'enviar',
  resultado: 'enviar',
  produto: 'enviar',
  grupo: 'enviar',
  base: 'base',
  historico: 'historico',
  usuarios: 'usuarios',
  cargas: 'cargas',
  carga: 'cargas',
  acuracia: 'acuracia',
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
  document.body.classList.toggle('na-entrada', rota.nome === 'entrar');
  document.body.classList.toggle('na-envio', rota.nome === 'enviar');
  document.body.classList.toggle('visual-painel', rota.nome === 'resultado' || rota.nome === 'produto');
  document.body.classList.toggle('na-resultado', rota.nome === 'resultado' || rota.nome === 'produto');
  document.body.classList.toggle('na-produto', rota.nome === 'produto');
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

  if (rota.nome !== 'entrar') {
    const usuario = await exigirSessao();
    if (!usuario) {
      return;
    }
    mostrarQuemEsta(usuario);
  }

  try {
    await modulo.desenhar(tela, rota.parametros, rota);
  } catch (erro) {
    trocar(tela, [falha(erro)]);
    throw erro;
  }
}

function mostrarQuemEsta(usuario) {
  const caixa = document.getElementById('quem-esta');
  if (caixa) {
    trocar(caixa, [
      el('span', { texto: usuario.nome + ' · ' + usuario.rotuloDoPerfil }),
      el('a', { href: '#/senha', texto: 'Trocar senha' }),
      el('button', {
        type: 'button', classe: 'botao', texto: 'Sair',
        aoClicar: async () => {
          await sair();
          window.location.href = 'index.html#/entrar';
          window.location.reload();
        },
      }),
    ]);
  }
  ajustarAoPerfil(usuario);
}

aoTrocarDeRota(() => {
  desenhar();
});
