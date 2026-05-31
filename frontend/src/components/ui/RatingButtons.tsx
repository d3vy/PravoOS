interface RatingButtonsProps {
  rating: number | null | undefined
  onRate: (rating: number) => void
  disabled?: boolean
}

export function RatingButtons({ rating, onRate, disabled = false }: RatingButtonsProps): JSX.Element {
  return (
    <div className="flex items-center gap-1.5">
      <button
        type="button"
        onClick={() => onRate(1)}
        disabled={disabled}
        aria-label="Полезный ответ"
        aria-pressed={rating === 1}
        className={`p-1.5 rounded-lg transition-colors disabled:opacity-50 ${
          rating === 1
            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400'
            : 'text-light-secondary dark:text-dark-secondary hover:bg-light-bg dark:hover:bg-dark-bg'
        }`}
      >
        <ThumbIcon direction="up" />
      </button>
      <button
        type="button"
        onClick={() => onRate(-1)}
        disabled={disabled}
        aria-label="Бесполезный ответ"
        aria-pressed={rating === -1}
        className={`p-1.5 rounded-lg transition-colors disabled:opacity-50 ${
          rating === -1
            ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
            : 'text-light-secondary dark:text-dark-secondary hover:bg-light-bg dark:hover:bg-dark-bg'
        }`}
      >
        <ThumbIcon direction="down" />
      </button>
    </div>
  )
}

function ThumbIcon({ direction }: { direction: 'up' | 'down' }): JSX.Element {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={direction === 'down' ? 'rotate-180' : ''}
    >
      <path d="M7 10v12" />
      <path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z" />
    </svg>
  )
}
