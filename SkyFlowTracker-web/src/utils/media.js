/** Map backend media URLs to the same-origin media proxy without a private host. */
export function toMediaUrl(value, basePath = import.meta.env?.VITE_MEDIA_BASE_PATH || '/SkyFlowTracker') {
  if (typeof value !== 'string' || !value.trim()) return ''
  const input = value.trim()
  let url
  try {
    url = new URL(input, 'http://localhost/')
  } catch {
    return ''
  }
  if (!['http:', 'https:'].includes(url.protocol)) return ''

  const prefix = '/' + basePath.replace(/^\/+|\/+$/g, '')
  if (url.pathname === prefix || url.pathname.startsWith(prefix + '/')) {
    return '/nginx' + url.pathname.slice(prefix.length) + url.search + url.hash
  }
  // Some API responses contain a media-relative path rather than an absolute URL.
  if (/^\/?videos\//.test(input)) return '/nginx' + url.pathname + url.search + url.hash
  return input
}
