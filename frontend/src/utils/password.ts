export function validatePassword(password: string): string | undefined {
  if (password.length < 8) return 'Пароль — не менее 8 символов'
  if (!/[a-zA-Zа-яА-ЯёЁ]/.test(password)) return 'Пароль должен содержать хотя бы одну букву'
  if (!/\d/.test(password)) return 'Пароль должен содержать хотя бы одну цифру'
  return undefined
}
