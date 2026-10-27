import CryptoJS from 'crypto-js';

/**
 * The stream URL's cipher (the Android app's JioSaavnService.decryptUrl):
 * DES in ECB with PKCS5 padding over standard Base64, under the fixed key
 * `38346591` — JioSaavn's own scheme. WebCrypto has nothing for DES, so this
 * is crypto-js, the same library the Android/web JioSaavn ecosystem uses for
 * exactly this ciphertext.
 */
const KEY = CryptoJS.enc.Utf8.parse('38346591');

/** The plaintext URL inside [encryptedUrl], or '' when it won't decode (matching the Android catch). */
export function decryptUrl(encryptedUrl: string): string {
  if (!encryptedUrl.trim()) return '';
  try {
    const decrypted = CryptoJS.DES.decrypt(
      { ciphertext: CryptoJS.enc.Base64.parse(encryptedUrl) } as CryptoJS.lib.CipherParams,
      KEY,
      { mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7 },
    );
    return decrypted.toString(CryptoJS.enc.Utf8).trim();
  } catch {
    return '';
  }
}
