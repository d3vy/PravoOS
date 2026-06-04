import { motion } from 'framer-motion'
import type { ComponentPropsWithoutRef, ReactNode } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md' | 'lg'

interface ButtonProps extends Omit<ComponentPropsWithoutRef<'button'>, 'children'> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  children: ReactNode
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-light-accent hover:bg-light-accent-hover dark:bg-dark-accent dark:hover:bg-dark-accent-hover text-white dark:text-dark-bg shadow-sm',
  secondary:
    'bg-transparent border border-light-border dark:border-dark-border text-light-text dark:text-dark-text hover:bg-light-surface dark:hover:bg-dark-surface',
  ghost:
    'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-surface dark:hover:bg-dark-surface',
  danger:
    'bg-red-600 hover:bg-red-700 text-white shadow-sm',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-3.5 py-1.5 text-sm',
  md: 'px-5 py-2.5 text-sm',
  lg: 'px-7 py-3.5 text-base',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  children,
  className = '',
  ...props
}: ButtonProps): JSX.Element {
  const isDisabled = disabled || loading

  return (
    <motion.button
      whileHover={isDisabled ? {} : { scale: 1.01 }}
      whileTap={isDisabled ? {} : { scale: 0.99 }}
      transition={{ duration: 0.1 }}
      className={`
        relative inline-flex items-center justify-center gap-2 rounded-lg font-medium
        transition-colors duration-150 focus:outline-none focus-visible:ring-2
        focus-visible:ring-light-text/30 dark:focus-visible:ring-dark-text/30 focus-visible:ring-offset-2
        focus-visible:ring-offset-light-bg dark:focus-visible:ring-offset-dark-bg
        disabled:opacity-40 disabled:cursor-not-allowed
        ${variantClasses[variant]}
        ${sizeClasses[size]}
        ${className}
      `}
      disabled={isDisabled}
      aria-busy={loading}
      {...(props as ComponentPropsWithoutRef<typeof motion.button>)}
    >
      {loading && (
        <span className="absolute inset-0 inline-flex items-center justify-center gap-1" aria-hidden="true">
          <span className="w-1.5 h-1.5 rounded-full bg-current animate-bounce [animation-delay:0ms]" />
          <span className="w-1.5 h-1.5 rounded-full bg-current animate-bounce [animation-delay:150ms]" />
          <span className="w-1.5 h-1.5 rounded-full bg-current animate-bounce [animation-delay:300ms]" />
        </span>
      )}
      <span className={loading ? 'invisible' : 'contents'}>{children}</span>
    </motion.button>
  )
}
