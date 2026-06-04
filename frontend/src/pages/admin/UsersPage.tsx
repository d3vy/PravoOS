import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { adminApi } from '../../api/admin'
import type { LawyerProfileResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'

export default function UsersPage(): JSX.Element {
  const { data: lawyers = [], isLoading } = useQuery<LawyerProfileResponse[]>({
    queryKey: ['admin-lawyers'],
    queryFn: adminApi.getLawyers,
  })

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8">
        <h1 className="font-display text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Юристы</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Активные пользователи платформы
        </p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : lawyers.length === 0 ? (
        <div className="text-center py-16 rounded-xl border border-dashed border-light-border dark:border-dark-border">
          <p className="text-light-secondary dark:text-dark-secondary text-sm">
            Нет активных юристов
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {lawyers.map((lawyer, index) => (
            <LawyerCard key={lawyer.userId} lawyer={lawyer} index={index} />
          ))}
        </div>
      )}
    </div>
  )
}

function LawyerCard({ lawyer, index }: { lawyer: LawyerProfileResponse; index: number }): JSX.Element {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, delay: index * 0.04 }}
      className="bg-light-surface dark:bg-dark-surface rounded-xl border border-light-border dark:border-dark-border p-5"
    >
      <div className="flex flex-col sm:flex-row sm:items-center gap-4">
        <div className="w-10 h-10 rounded-full bg-light-accent/10 dark:bg-dark-accent/10 flex items-center justify-center shrink-0">
          <span className="text-sm font-semibold text-light-accent dark:text-dark-accent">
            {(lawyer.fullName ?? lawyer.email).charAt(0).toUpperCase()}
          </span>
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-semibold text-light-text dark:text-dark-text">
            {lawyer.fullName ?? '—'}
          </p>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">{lawyer.email}</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-x-8 gap-y-1 text-sm">
          <InfoField label="Номер адвоката" value={lawyer.barNumber} />
          <InfoField label="Специализация" value={lawyer.specialization} />
          <InfoField label="Телефон" value={lawyer.phone} />
        </div>
      </div>
    </motion.div>
  )
}

function InfoField({ label, value }: { label: string; value: string | null }): JSX.Element {
  return (
    <div>
      <span className="text-xs text-light-secondary dark:text-dark-secondary">{label}</span>
      <p className="text-light-text dark:text-dark-text truncate">{value ?? '—'}</p>
    </div>
  )
}
