# Casa

App para **tablet Android e iPad** (funciona também em celulares e iPhone) que junta todas as marcas da casa
num painel só, no estilo do Apple Home: luzes, interruptores, tomadas, TV, câmeras,
aspirador robô, controle infravermelho, Alexa, previsão do tempo e nível dos rios.

Funciona para qualquer casa: nenhum aparelho, senha ou endereço fica fixo no código —
cada pessoa conecta o que tem em **Ajustes**.

| Tablet | Celular |
| --- | --- |
| ![Tela inicial no tablet](docs/tablet-inicio.png) | ![Tela inicial no celular](docs/celular-inicio.png) |

## O que cada aparelho usa

| Aparelho | Como o app conversa com ele | Precisa de |
| --- | --- | --- |
| Interruptores, tomadas e lâmpadas Wi-Fi que funcionam no **Smart Life / Tuya** (a maioria das marcas nacionais) | Nuvem Tuya | Projeto grátis em iot.tuya.com + app Smart Life |
| Controles infravermelho Wi-Fi compatíveis com Smart Life | Nuvem Tuya (hub IR) | O mesmo projeto Tuya |
| TVs **LG** (webOS), **Samsung** (Tizen) e **Roku / Roku TV** | Rede local, sem nuvem | IP da TV; MAC para ligá-la pelo app |
| Câmeras IP com RTSP (EZVIZ, ICSee/XMEye, ONVIF e outras) | RTSP direto da câmera, sem nuvem | IP e senha / código de verificação |
| Robôs aspiradores, Alexa e o que mais o Home Assistant reconhecer | Home Assistant | Home Assistant na rede |
| Previsão do tempo | [Open-Meteo](https://open-meteo.com) (dados de serviços meteorológicos oficiais) | Só escolher a cidade |
| Nível dos rios | Painel público das Defesas Civis municipais (ex.: Rio do Sul - SC) | Cidade atendida pelo painel |

Vários fabricantes (Amazon, Eufy, Xiaomi, entre outros) não oferecem acesso aberto aos aparelhos para outros apps. O
[Home Assistant](https://www.home-assistant.io/) — gratuito, roda num Raspberry Pi,
mini PC ou máquina virtual — faz essa ponte dentro de casa, e o app conversa com ele.
Ele é opcional: sem ele, tudo o mais continua funcionando.

## Recursos

- **Início**: relógio, resumo da casa (luzes acesas, aparelhos ligados, aspirador, temperatura),
  cenas do Smart Life e do Home Assistant, aparelhos agrupados por cômodo. Toque liga/desliga;
  toque e segure abre os detalhes (brilho, comandos do aspirador, nome, cômodo, favorito, ocultar).
- **Voz**: botão de microfone entende português — “apagar as luzes da sala”, “ligar a cafeteira”,
  “aspirador volta pra base”, “ativar modo cinema”. O que não for da casa vai para a Alexa.
- **Clima**: temperatura, sensação térmica, condição do céu, umidade, vento com direção, rajadas,
  chance e volume de chuva, índice UV, pressão, nebulosidade, nascer e pôr do sol, as próximas
  24 horas e os próximos 7 dias. Aparece resumido na tela inicial e responde a “vai chover hoje?”.
- **Nível do rio**: leitura atual, faixa (Normal, Atenção, Alerta, Emergência), tendência em cm/h,
  gráfico das últimas 24 h e as demais estações da cidade, direto do painel da Defesa Civil.
  Aparece sozinho ao escolher no clima uma cidade atendida; também em Ajustes › Nível do rio.
- **Câmeras**: grade ao vivo (stream reduzido) e tela cheia com som (stream principal). Câmeras
  solares ou a bateria podem ficar **só de dia** ou **só quando tocar**, com desligamento
  automático (30 s a 5 min) e a última imagem vista enquanto descansam.
- **TV**: uma ou várias TVs (LG, Samsung, Roku). Liga/desliga, direcional, voltar/início/menu,
  volume, canais, entradas HDMI e os apps instalados na TV com os ícones de verdade.
  Outras marcas entram pelo Home Assistant ou pelo controle infravermelho.
- **Controles**: botões dos controles infravermelho cadastrados no Smart Life e painel de
  ar-condicionado (temperatura, modo, ventilação).
- **Alexa**: comando por voz ou texto executado pelo Echo, anúncios, fala, “tocando agora” e volume.
- **Usar em outro aparelho**: um código cifrado leva toda a configuração para outro celular ou
  tablet — veja abaixo.
- **Modo tablet de parede**: tela sempre ligada e tela cheia opcionais.

## Instalação no Android

1. Baixe o `app-release.apk` (aba **Actions** do GitHub › workflow *APK* › última execução ›
   *casa-apk*, ou gere localmente, abaixo).
2. No tablet, abra o arquivo e permita “instalar apps desconhecidos” para o navegador ou gerenciador
   de arquivos.
3. Abra **Casa › Ajustes** e conecte as integrações. Cada uma tem um “Como configurar” passo a passo.

Requer Android 8.0 ou mais novo.

## Instalação no iPhone e iPad

O app iOS é gerado num Mac do próprio GitHub: aba **Actions** › workflow *IPA (iOS)* ›
última execução › artefato *casa-ipa* (um zip com o `Casa.ipa`). Para gerar de novo, use
**Run workflow** nessa mesma tela.

Sem conta de desenvolvedor paga, a instalação é feita pelo computador:

1. Instale o [Sideloadly](https://sideloadly.io) (Windows ou Mac) ou o [AltStore](https://altstore.io).
2. Conecte o iPhone/iPad por cabo, arraste o `Casa.ipa` para o Sideloadly, entre com o seu Apple ID
   e toque em *Start*.
3. No aparelho: *Ajustes › Geral › VPN e Gerenciamento de Dispositivo* › confie no seu Apple ID.
   No iOS 16 ou mais novo, ative também *Ajustes › Privacidade e Segurança › Modo de Desenvolvedor*.
4. Na primeira abertura, permita o acesso à **rede local** — é por ela que o app fala com a TV,
   as câmeras e o Home Assistant.

Com Apple ID gratuito a instalação vale **7 dias**. Com a conta de desenvolvedor da Apple
(US$ 99/ano) vale 1 ano e dá para instalar pelo TestFlight, sem computador.

### iPhone de uso diário, sem pagar: SideStore

O limite de 7 dias é da Apple e não tem como evitar sem a conta paga. O [SideStore](https://sidestore.io)
renova o app **pelo próprio iPhone**, sem depender do computador ligado:

1. Siga o guia oficial do SideStore uma única vez (usa o computador para instalar o SideStore e
   gerar o arquivo de pareamento, e um app de VPN local da App Store indicado no guia).
2. Envie o `Casa.ipa` para o iPhone (AirDrop, iCloud Drive ou Arquivos) e, no SideStore, toque em
   **+** e escolha o arquivo.
3. Para nunca vencer: no app **Atalhos › Automação**, crie uma automação diária que execute
   **Atualizar todos os apps** do SideStore.

Limites do Apple ID gratuito: no máximo 3 apps instalados assim ao mesmo tempo (o SideStore conta
como um). Atualizações do iOS às vezes exigem refazer o pareamento.

Diferenças no iOS:

- **Voz**: o microfone abre um campo de comando; toque no microfone do teclado para ditar.
- **Ligar a TV pela rede**: o iOS bloqueia o sinal de Wake-on-LAN para apps sem uma permissão
  especial da Apple. A Roku liga normalmente; LG e Samsung, ligue pelo controle, pelo hub
  infravermelho ou pela Alexa — o resto do controle da TV funciona.
- **Câmeras**: o vídeo ao vivo usa o VLC; a última imagem das câmeras em repouso não é guardada.

Requer iOS 15 ou mais novo.

### Gerar o APK localmente

```bash
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # testes + capturas das telas em app/build/outputs/roborazzi
```

Sem configuração extra o APK sai assinado com a chave de depuração. Para uma chave própria
(recomendado, para atualizar o app sem desinstalar), crie `keystore.properties` na raiz
— ele não vai para o Git:

```properties
storeFile=casa.jks
storePassword=...
keyAlias=casa
keyPassword=...
```

![Previsão do tempo no tablet](docs/tablet-clima.png)

## Configuração

### Previsão do tempo

Em **Clima** (ou Ajustes › Clima), busque a sua cidade. Não precisa de conta nem chave.
O app usa o [Open-Meteo](https://open-meteo.com) — gratuito para uso pessoal, com dados de
institutos meteorológicos nacionais — e atualiza a cada 30 minutos com o app aberto.
A WeatherKit da Apple e a Weather API do Google exigem conta de desenvolvedor paga ou cobrança
no cartão, por isso não foram usadas.

### Smart Life / Tuya (interruptores, tomadas, lâmpadas, controle IR)

1. Crie uma conta em [iot.tuya.com](https://iot.tuya.com) › *Cloud* › *Create Cloud Project*
   (Smart Home, data center **Western America** para contas do Brasil).
2. *Devices › Link Tuya App Account*: leia o QR code com o app **Smart Life**.
   Apps de marca própria (feitos sobre a Tuya) normalmente não podem ser vinculados — adicione
   os mesmos aparelhos ao Smart Life.
3. *Service API*: ative **IoT Core**, **Authorization Token Management**,
   **Smart Home Scene Linkage** e **IR Control Hub Open Service**.
4. Copie *Access ID* e *Access Secret* para o app.

O plano de testes da Tuya é gratuito, mas precisa ser renovado periodicamente no mesmo site.

### TVs

Em **Ajustes › TVs › Adicionar TV**, escolha a marca e informe o IP (e o MAC, para ligar pela
rede). Reserve um IP fixo para cada TV no roteador. Dá para cadastrar quantas TVs quiser.

- **LG (webOS)**: toque em **Autorizar** e aceite o pedido na TV. Para ligar pelo app, ative
  “Ligar via Wi-Fi” nas configurações gerais da TV.
- **Samsung (Tizen, 2016 em diante)**: toque em **Autorizar** e permita o acesso na TV.
- **Roku / Roku TV**: não precisa autorizar. Se não responder, ative *Configurações › Sistema ›
  Configurações avançadas › Controle por apps móveis*.
- **Outras marcas**: pelo Home Assistant (entidade `media_player`) ou pelo controle infravermelho.

### Usar em outro celular ou tablet

Não é preciso refazer tudo em cada aparelho:

1. No aparelho já configurado: **Ajustes › Outros aparelhos › Enviar configuração**. Crie uma
   senha (mínimo 6 caracteres) e toque em **Gerar código**.
2. Envie o código por WhatsApp, e-mail ou como preferir — e a senha **por outro caminho**
   (fale pessoalmente ou mande separado).
3. No aparelho novo: na tela de boas-vindas, **Já uso em outro aparelho** (ou Ajustes › Outros
   aparelhos › Receber). Cole a mensagem inteira, digite a senha e toque em **Aplicar**.

O código leva integrações, TVs, câmeras, cômodos, clima e rio; as preferências de tela de cada
aparelho continuam as dele. Ele é cifrado com a senha (PBKDF2 + HMAC-SHA256) e não passa por
nenhum servidor: sem a senha, ninguém lê as credenciais. Quem receber código e senha tem acesso
aos seus aparelhos — compartilhe só com quem mora com você.

### Câmeras

- **EZVIZ**: usuário `admin`, senha = código de verificação de 6 letras da etiqueta. Se não abrir,
  desative a “criptografia de imagem” no app EZVIZ.
- **ICSee/XMEye**: usuário `admin` e a senha do app ICSee (vazia se nunca definida).
- **Solares / a bateria**: em Ajustes › Câmeras, toque na câmera e escolha “Ao vivo só de dia”
  ou “Só quando tocar”. Muitos modelos a bateria dormem fora da transmissão e não aceitam RTSP;
  nesses casos o app oferece “Abrir no app iCSee”.
- **Outras**: qualquer endereço RTSP/ONVIF.

### Home Assistant (aspirador, Alexa e outros)

1. Adicione no Home Assistant as integrações dos seus aparelhos. Para a Alexa, instale o
   [HACS](https://hacs.xyz) e, por ele, **Alexa Media Player**; para robôs aspiradores, a
   integração da marca (oficial ou do HACS).
2. Perfil › Segurança › *Tokens de acesso de longa duração* › Criar token.
3. No app: endereço (ex.: `http://192.168.0.10:8123`) e o token.

## Privacidade

O app não tem servidor próprio, conta, anúncios nem coleta de dados. O código de “Enviar
configuração” é gerado e lido no próprio aparelho. As credenciais ficam só no
aparelho — no Android cifradas com uma chave do Android Keystore, no iOS num arquivo com a proteção
de dados do sistema — e são enviadas apenas para o serviço a que
pertencem (Tuya, Home Assistant, TV, câmeras). Para a previsão do tempo, só as coordenadas da
cidade escolhida são enviadas ao Open-Meteo. Câmeras e TV são acessadas direto pela rede local.

## Estrutura

O código é um só para Android e iOS ([Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
com [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)):

```
shared/src/
├── commonMain/      telas, integrações e regras — 100% compartilhado
│   ├── core/        configuração, rede (Ktor), formatação, SHA-256/HMAC
│   ├── integrations/  tuya/, tv/, ha/, camera/, weather/, river/
│   ├── home/        HomeRepository (junta tudo), clima, rio, interpretador de voz
│   └── ui/          tema, componentes e telas
├── androidMain/     ExoPlayer, Android Keystore, voz do Google, Wake-on-LAN
└── iosMain/         ponte com o VLC, armazenamento protegido, entrada do app
app/                 app Android (Activity, ícone, testes e capturas de tela)
iosApp/              app iOS (SwiftUI + VLC), gerado pelo XcodeGen
```

Para incluir uma nova marca, implemente `DeviceProvider` e registre-a no `HomeRepository`.

---

© 2026 [Mauricio Renato Sarda](https://micio.dev/). Todos os direitos reservados.
