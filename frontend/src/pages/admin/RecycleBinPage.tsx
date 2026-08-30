import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { RecycleBinList } from '../../components/recycleBin/RecycleBinList'

export default function RecycleBinPage(): JSX.Element {
  const { t } = useTranslation()

  return (
    <RecycleBinList
      queryKey="admin-recycle-bin"
      title={t('recycleBin.title')}
      description={t('recycleBin.description')}
      list={adminApi.getRecycleBin}
      restore={adminApi.restoreRecycleBinEntry}
      purge={adminApi.purgeRecycleBinEntry}
      showOrgFilter
    />
  )
}
