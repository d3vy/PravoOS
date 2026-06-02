import { useState, type FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { usersApi } from '../../api/users'
import type { LawyerProfileResponse, UpdateProfileRequest } from '../../types'
import { Navbar } from '../../components/layout/Navbar'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'

export default function ProfilePage(): JSX.Element {
  const queryClient = useQueryClient()

  const { data: profile, isLoading } = useQuery<LawyerProfileResponse>({
    queryKey: ['profile'],
    queryFn: usersApi.getProfile,
  })

  if (isLoading) {
    return (
      <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
        <Navbar />
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
        <Navbar />
        <div className="page-container py-16 text-center">
          <p className="text-light-secondary dark:text-dark-secondary">Профиль не найден</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8 max-w-lg">
        <h1 className="font-display text-3xl font-semibold text-light-text dark:text-dark-text mb-8">
          Профиль
        </h1>
        <ProfileForm profile={profile} queryClient={queryClient} />
      </div>
    </div>
  )
}

function ProfileForm({
  profile,
  queryClient,
}: {
  profile: LawyerProfileResponse
  queryClient: ReturnType<typeof useQueryClient>
}): JSX.Element {
  const [fullName, setFullName] = useState(profile.fullName)
  const [barNumber, setBarNumber] = useState(profile.barNumber ?? '')
  const [specialization, setSpecialization] = useState(profile.specialization ?? '')
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [success, setSuccess] = useState(false)

  const updateMutation = useMutation({
    mutationFn: (data: UpdateProfileRequest) => usersApi.updateProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      setSuccess(true)
      setTimeout(() => setSuccess(false), 3000)
    },
  })

  const handleSubmit = (e: FormEvent): void => {
    e.preventDefault()
    updateMutation.mutate({
      fullName,
      barNumber: barNumber || undefined,
      specialization: specialization || undefined,
      phone: phone || undefined,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <label className="text-sm font-medium text-light-text dark:text-dark-text block mb-1.5">Email</label>
        <input
          type="email"
          value={profile.email}
          disabled
          className="input-base opacity-60 cursor-not-allowed w-full"
        />
      </div>

      <Input
        label="ФИО"
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        required
        maxLength={255}
      />

      <Input
        label="Номер удостоверения адвоката"
        value={barNumber}
        onChange={(e) => setBarNumber(e.target.value)}
        maxLength={100}
        placeholder="Необязательно"
      />

      <Input
        label="Специализация"
        value={specialization}
        onChange={(e) => setSpecialization(e.target.value)}
        maxLength={255}
        placeholder="Необязательно"
      />

      <Input
        label="Телефон"
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        maxLength={50}
        placeholder="Необязательно"
      />

      {updateMutation.isError && (
        <p className="text-sm text-red-600 dark:text-red-400">Ошибка сохранения. Попробуйте снова.</p>
      )}

      {success && (
        <p className="text-sm text-green-600 dark:text-green-400">Профиль сохранён</p>
      )}

      <div className="pt-2">
        <Button
          type="submit"
          variant="primary"
          loading={updateMutation.isPending}
          disabled={!fullName.trim()}
        >
          Сохранить
        </Button>
      </div>
    </form>
  )
}
