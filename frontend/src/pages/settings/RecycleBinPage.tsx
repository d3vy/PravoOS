import { useTranslation } from 'react-i18next'
import { recycleBinApi } from '../../api/recycleBin'
import { RecycleBinList } from '../../components/recycleBin/RecycleBinList'

export default function RecycleBinPage(): JSX.Element {
  const { t } = useTranslation()

  return (
    <RecycleBinList
      queryKey="recycle-bin"
      title={t('recycleBin.title')}
      description={t('recycleBin.description')}
      list={recycleBinApi.list}
      items={recycleBinApi.items}
      restore={recycleBinApi.restore}
    />
  )
}
