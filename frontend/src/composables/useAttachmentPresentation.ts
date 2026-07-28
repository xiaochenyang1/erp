type FormatNumber = (
  value: number,
  options?: Intl.NumberFormatOptions
) => string

/** Localized display helpers for attachment metadata. */
export const useAttachmentPresentation = (options: {
  formatNumber: FormatNumber
}) => {
  const formatFileSize = (size: number) => {
    if (size < 1024) return `${options.formatNumber(size)} B`
    if (size < 1024 * 1024) {
      return `${options.formatNumber(size / 1024, { maximumFractionDigits: 1 })} KB`
    }
    return `${options.formatNumber(size / 1024 / 1024, { maximumFractionDigits: 1 })} MB`
  }

  return {
    formatFileSize
  }
}
