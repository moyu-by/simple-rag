import { JSEncrypt } from 'jsencrypt'

let cachedPublicKey: string | null = null

export async function getPublicKey(): Promise<string> {
  if (cachedPublicKey) return cachedPublicKey
  const { default: request } = await import('@/api/request')
  const res = await request.get('/auth/public-key')
  cachedPublicKey = res.data.publicKey
  return cachedPublicKey!
}

export function encryptWithRSA(plaintext: string, publicKey: string): string {
  const encrypt = new JSEncrypt()
  encrypt.setPublicKey(publicKey)
  const encrypted = encrypt.encrypt(plaintext)
  if (!encrypted) throw new Error('RSA 加密失败')
  return encrypted
}

export async function encryptPassword(password: string): Promise<string> {
  const pubKey = await getPublicKey()
  return encryptWithRSA(password, pubKey)
}
