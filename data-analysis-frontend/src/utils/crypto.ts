import CryptoJS from 'crypto-js'

const ENCRYPTION_KEY = 'datamine2024secr'

export function encryptPassword(password: string): string {
  if (!password) return password
  const key = CryptoJS.enc.Utf8.parse(ENCRYPTION_KEY)
  const encrypted = CryptoJS.AES.encrypt(password, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  })
  return encrypted.toString()
}
