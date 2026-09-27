# Cadastro Facial (Hikvision ISAPI)

App Android para cadastrar um usuário (nome, foto facial, cartão, validade) e
enviar esse cadastro simultaneamente para vários terminais Hikvision de um
mesmo prédio, escolhendo na lista quais equipamentos recebem.

Compatível com terminais que suportam o protocolo ISAPI de controle de
acesso/reconhecimento facial, incluindo as famílias DS-K1T671/672/683 e
DS-K1T341/342.

## Como usar
1. Crie um **prédio**
2. Dentro do prédio, cadastre os **equipamentos** (nome/apelido, IP, porta,
   usuário e senha de acesso ao terminal — geralmente `admin` + a senha
   definida na instalação)
3. Toque em **"Novo cadastro nesses equipamentos"**
4. Preencha nome, foto (câmera ou galeria), cartão (opcional) e validade
5. Marque quais equipamentos devem receber esse cadastro (todos vêm
   marcados por padrão)
6. Toque em **Enviar cadastro** — o app mostra o resultado equipamento por
   equipamento
7. Se algum falhar (ex: código de usuário já existe nesse equipamento),
   toque em **"Ver / tentar de novo"**, ajuste o código e reenvie só pra
   aquele equipamento

## Sobre o código do usuário (employeeNo)
Gerado automaticamente no formato `AP` + números até completar 7 caracteres
(ex: `AP00001`). É editável antes de enviar e também na tela de retentativa,
caso algum equipamento já tenha esse código em uso.

## Limitações e observações técnicas
- A rede do prédio precisa estar acessível pelo celular (mesmo Wi-Fi local,
  ou VPN) — o ISAPI é local ao equipamento, não passa pela nuvem da Hikvision
- Testado a nível de protocolo com base na documentação pública do ISAPI e
  em relatos de integração real (autenticação Digest, upload de foto via
  multipart). **Pode ser necessário ajustar detalhes finos dependendo da
  versão de firmware de cada modelo** — se um equipamento específico
  recusar a foto ou o cadastro com um erro estranho, me avise com a
  mensagem de erro exata que aparece no app para eu ajustar
- Card (cartão) e validade são opcionais; se o cartão ficar em branco, só
  não é enviado, mas o cadastro do usuário + foto segue normalmente
- A cada tentativa de comunicação, o app gera um desafio de autenticação
  novo (não reaproveita), porque alguns firmwares derrubam a conexão do
  upload de foto se a mesma sessão de autenticação for reaproveitada

## Como subir pro GitHub pelo Termux
```
cd caminho/para/facialapp
git init
git add .
git commit -m "Primeira versão do Cadastro Facial"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/SEU_REPOSITORIO.git
git push -u origin main
```
Depois, aba **Actions** do repositório → esperar o build terminar → baixar
o artefato **FacialApp-debug-apk**.
