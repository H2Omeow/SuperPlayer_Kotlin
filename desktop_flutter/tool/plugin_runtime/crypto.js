import CryptoJS from 'crypto-js';
import { Buffer } from 'buffer';
import forge from 'node-forge/lib/forge.js';
import 'node-forge/lib/rsa.js';
import 'node-forge/lib/pem.js';
import 'node-forge/lib/random.js';
import { randomBytes } from './platform.js';

export { CryptoJS, randomBytes };

const words = (value) => CryptoJS.enc.Hex.parse(Buffer.from(value).toString('hex'));
const bytes = (value) => Buffer.from(value.toString(CryptoJS.enc.Hex), 'hex');

CryptoJS.lib.WordArray.random = (length) => words(randomBytes(length));
forge.random.getBytesSync = (length) => randomBytes(length).toString('latin1');

export function aesEncrypt(input, algorithm, key, iv) {
  const match = /^aes-(128|192|256)-(cbc|ecb|ctr|cfb|ofb)$/i.exec(String(algorithm));
  if (!match) throw new Error(`Unsupported AES algorithm: ${algorithm}`);
  const keyBytes = Buffer.from(key);
  if (keyBytes.length !== Number(match[1]) / 8) throw new Error('Invalid AES key length');
  const mode = match[2].toLowerCase();
  const ivBytes = iv == null ? Buffer.alloc(0) : Buffer.from(iv);
  if (ivBytes.length !== (mode === 'ecb' ? 0 : 16)) throw new Error('Invalid AES IV length');
  return bytes(CryptoJS.AES.encrypt(words(input), words(keyBytes), {
    iv: words(ivBytes), mode: CryptoJS.mode[mode.toUpperCase()],
    padding: ['cbc', 'ecb'].includes(mode) ? CryptoJS.pad.Pkcs7 : CryptoJS.pad.NoPadding,
  }).ciphertext);
}

export function rsaEncrypt(input, key) {
  const decoded = forge.pem.decode(String(key))[0];
  if (!decoded || !['PUBLIC KEY', 'RSA PUBLIC KEY'].includes(decoded.type)) throw new Error('RSA public key PEM required');
  const publicKey = forge.pki.publicKeyFromAsn1(forge.asn1.fromDer(decoded.body));
  const length = Math.ceil(publicKey.n.bitLength() / 8);
  const data = Buffer.from(input);
  if (data.length > length) throw new Error('LX RSA input is too large');
  const padded = Buffer.concat([Buffer.alloc(length - data.length), data]);
  return Buffer.from(publicKey.encrypt(padded.toString('latin1'), 'RAW'), 'latin1');
}

export const cryptoModule = {
  randomBytes,
  createHash(algorithm) {
    const hash = CryptoJS.algo[String(algorithm).replace(/-/g, '').toUpperCase()];
    if (!hash) throw new Error(`Unsupported hash algorithm: ${algorithm}`);
    const state = hash.create();
    let finalized = false;
    return {
      update(value, encoding) {
        if (finalized) throw new Error('Hash already finalized');
        state.update(words(typeof value === 'string' ? Buffer.from(value, encoding || 'utf8') : value));
        return this;
      },
      digest(encoding) {
        if (finalized) throw new Error('Hash already finalized');
        finalized = true;
        const result = bytes(state.finalize());
        return encoding ? result.toString(encoding) : result;
      },
    };
  },
};

export const lxCrypto = Object.freeze({
  aesEncrypt, rsaEncrypt, randomBytes,
  md5: (value) => CryptoJS.MD5(typeof value === 'string' ? value : words(value)).toString(),
});
