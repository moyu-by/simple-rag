declare module 'jsencrypt' {
  export class JSEncrypt {
    setPublicKey(key: string): void
    encrypt(plaintext: string): string | false
    decrypt(ciphertext: string): string | false
  }
}
