<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import Slider from 'primevue/slider'
import { useToast } from 'primevue/usetoast'
import { getFlightsList, getFlightDetail, deleteFlights } from '../api/flights'
import { getAllEnabledFlyZones } from '../api/flyZones'
import { checkToken } from '../api/auth'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const toast = useToast()
const isAdmin = ref(false)
const loading = ref(true)
const flights = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedFlights = ref([])
const deleteLoading = ref(false)
const searchKeyword = ref('')

// 当前选中的飞行记录
const selectedFlight = ref(null)
const flightDetail = ref(null)
const detailLoading = ref(false)

// ==================== 地图 + 播放相关 ====================
const mapContainer = ref(null)
let map = null
let polyline = null
let activePolyline = null
let droneMarker = null
let homeMarker = null

const flightPath = ref([])
const isPlaying = ref(false)
const playProgress = ref(0)
const playSpeed = ref(1)
let animFrameId = null       // requestAnimationFrame ID
let animStartWallTime = null // 动画开始时的真实时间
let animStartFlightTime = 0  // 动画开始时对应的飞行时间偏移（ms）
const currentFlightTimeMs = ref(0) // 当前播放到的飞行时间（相对于第一个点，ms）

// ==================== 禁飞区图层 ====================
const flyZones = ref([])
let flyZoneLayers = []
const showFlyZones = ref(true)

const flyZoneColors = {
  0: { stroke: '#FFD700', fill: 'rgba(255, 215, 0, 0.15)', name: '警告区' },
  1: { stroke: '#FF8C00', fill: 'rgba(255, 140, 0, 0.15)', name: '增强警告区' },
  2: { stroke: '#4169E1', fill: 'rgba(65, 105, 225, 0.15)', name: '授权区' },
  3: { stroke: '#DC143C', fill: 'rgba(220, 20, 60, 0.15)', name: '禁飞区' }
}

const loadFlyZones = async () => {
  try {
    const res = await getAllEnabledFlyZones()
    if (res.code === 1) {
      flyZones.value = res.data || []
      renderFlyZones()
    }
  } catch (e) {
    console.error('加载禁飞区失败:', e)
  }
}

const renderFlyZones = () => {
  if (!map) return
  flyZoneLayers.forEach(l => map.removeLayer(l))
  flyZoneLayers = []

  // 按 category 升序排列：低级别区域先渲染（底层），禁飞区最后渲染（顶层可点击）
  const sorted = [...flyZones.value].sort((a, b) => a.category - b.category)
  sorted.forEach(zone => {
    const color = flyZoneColors[zone.category] || flyZoneColors[3]
    let layer
    if (zone.shape === 0 && zone.centerLat && zone.centerLng) {
      layer = L.circle([zone.centerLat, zone.centerLng], {
        radius: zone.radius || 500,
        color: color.stroke, fillColor: color.fill,
        fillOpacity: 0.15, weight: 2
      })
    } else if (zone.shape === 1 && zone.polygonPoints) {
      const pts = (typeof zone.polygonPoints === 'string' ? JSON.parse(zone.polygonPoints) : zone.polygonPoints)
        .map(p => [p.lat, p.lng])
      if (pts.length >= 3) {
        layer = L.polygon(pts, {
          color: color.stroke, fillColor: color.fill,
          fillOpacity: 0.15, weight: 2
        })
      }
    }
    if (layer) {
      layer.bindPopup(`<b>${zone.name}</b><br>类型: ${color.name}${zone.maxAltitude ? '<br>限高: ' + zone.maxAltitude + 'm' : ''}`)
      layer.addTo(map)
      flyZoneLayers.push(layer)
    }
  })
}

const toggleFlyZones = () => {
  showFlyZones.value = !showFlyZones.value
  flyZoneLayers.forEach(l => {
    if (showFlyZones.value) l.addTo(map)
    else map.removeLayer(l)
  })
}

const speedOptions = [
  { label: '0.5x', value: 0.5 },
  { label: '1x', value: 1 },
  { label: '2x', value: 2 },
  { label: '4x', value: 4 },
  { label: '8x', value: 8 }
]

// 在两个原始点之间线性插值，返回当前插值点
const currentPoint = computed(() => {
  if (!flightPath.value.length) return null
  const pts = flightPath.value
  const t0 = pts[0].ts
  const elapsed = currentFlightTimeMs.value
  const absTime = t0 + elapsed

  // 找到 absTime 所在的区间 [i, i+1]
  let i = 0
  for (; i < pts.length - 1; i++) {
    if (pts[i + 1].ts >= absTime) break
  }
  if (i >= pts.length - 1) return pts[pts.length - 1]

  const p0 = pts[i]
  const p1 = pts[i + 1]
  const segDur = p1.ts - p0.ts
  const ratio = segDur > 0 ? Math.min((absTime - p0.ts) / segDur, 1) : 0

  return {
    lat: p0.lat + (p1.lat - p0.lat) * ratio,
    lng: p0.lng + (p1.lng - p0.lng) * ratio,
    alt: p0.alt != null && p1.alt != null ? p0.alt + (p1.alt - p0.alt) * ratio : (p0.alt ?? p1.alt),
    speed: p0.speed != null && p1.speed != null ? p0.speed + (p1.speed - p0.speed) * ratio : (p0.speed ?? p1.speed),
    heading: p0.heading != null && p1.heading != null ? p0.heading + (p1.heading - p0.heading) * ratio : (p0.heading ?? p1.heading),
    ts: absTime
  }
})

// 总时长（秒）
const totalDuration = computed(() => {
  if (flightPath.value.length < 2) return 0
  return Math.round((flightPath.value[flightPath.value.length - 1].ts - flightPath.value[0].ts) / 1000)
})

// 总时长（毫秒）
const totalDurationMs = computed(() => {
  if (flightPath.value.length < 2) return 0
  return flightPath.value[flightPath.value.length - 1].ts - flightPath.value[0].ts
})

// 当前播放时间（秒）
const currentPlayTime = computed(() => {
  return Math.round(currentFlightTimeMs.value / 1000)
})

// 格式化播放时间 mm:ss
const formatPlayTime = (seconds) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// 初始化地图
const initMap = () => {
  if (!mapContainer.value || map) return
  map = L.map(mapContainer.value, {
    center: [24.5, 118.1],
    zoom: 15,
    zoomControl: false
  })
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
    maxZoom: 19
  }).addTo(map)
  L.control.zoom({ position: 'topright' }).addTo(map)
}

// 清除地图标记
const clearMap = () => {
  if (!map) return
  const flyZoneSet = new Set(flyZoneLayers)
  const toRemove = []
  map.eachLayer(layer => {
    if (!(layer instanceof L.TileLayer) && !flyZoneSet.has(layer)) toRemove.push(layer)
  })
  toRemove.forEach(layer => map.removeLayer(layer))
  polyline = null
  activePolyline = null
  droneMarker = null
  homeMarker = null
}

// 销毁地图
const destroyMap = () => {
  stopPlay()
  if (map) { map.remove(); map = null }
}

// 渲染航线
const renderFlightOnMap = (detail) => {
  if (!map || !flightPath.value.length) return
  clearMap()
  const points = flightPath.value.map(p => [p.lat, p.lng])

  // 完整航线（灰色虚线）
  polyline = L.polyline(points, { color: '#9CA3AF', weight: 3, dashArray: '8, 6', opacity: 0.6 }).addTo(map)
  // 已播放航线（蓝色实线）
  activePolyline = L.polyline([], { color: '#2563EB', weight: 4, opacity: 0.9 }).addTo(map)

  // 起飞点 H
  const homeLl = detail.homeLatitude && detail.homeLongitude
    ? [detail.homeLatitude, detail.homeLongitude] : points[0]
  homeMarker = L.marker(homeLl, {
    icon: L.divIcon({
      className: 'home-marker',
      html: '<div class="home-icon">H</div>',
      iconSize: [32, 32], iconAnchor: [16, 16]
    })
  }).addTo(map)

  // 无人机图标
  droneMarker = L.marker(points[0], {
    icon: L.divIcon({
      className: 'drone-marker',
      html: '<div class="drone-icon"><svg viewBox="0 0 24 24" width="24" height="24" fill="#2563EB"><path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z"/></svg></div>',
      iconSize: [32, 32], iconAnchor: [16, 16]
    })
  }).addTo(map)

  map.fitBounds(polyline.getBounds().pad(0.1))
}

// 播放控制
const togglePlay = () => {
  if (!flightPath.value.length) return
  isPlaying.value ? pausePlay() : startPlay()
}

const startPlay = () => {
  if (totalDurationMs.value <= 0) return
  // 如果已到末尾，从头开始
  if (currentFlightTimeMs.value >= totalDurationMs.value) {
    currentFlightTimeMs.value = 0
    playProgress.value = 0
  }
  isPlaying.value = true
  animStartWallTime = performance.now()
  animStartFlightTime = currentFlightTimeMs.value
  animFrameId = requestAnimationFrame(animLoop)
}

const animLoop = (now) => {
  if (!isPlaying.value) return
  const wallElapsed = now - animStartWallTime
  const flightElapsed = animStartFlightTime + wallElapsed * playSpeed.value
  if (flightElapsed >= totalDurationMs.value) {
    currentFlightTimeMs.value = totalDurationMs.value
    playProgress.value = 100
    updateMapState()
    isPlaying.value = false
    return
  }
  currentFlightTimeMs.value = flightElapsed
  playProgress.value = Math.round((flightElapsed / totalDurationMs.value) * 100)
  updateMapState()
  animFrameId = requestAnimationFrame(animLoop)
}

const pausePlay = () => {
  isPlaying.value = false
  if (animFrameId) { cancelAnimationFrame(animFrameId); animFrameId = null }
}

const stopPlay = () => {
  pausePlay()
  currentFlightTimeMs.value = 0
  playProgress.value = 0
}

// 更新地图上的无人机位置和已播放航线
const updateMapState = () => {
  const point = currentPoint.value
  if (!point) return

  if (droneMarker) {
    droneMarker.setLatLng([point.lat, point.lng])
    if (point.heading != null) {
      const el = droneMarker.getElement()
      if (el) {
        const svg = el.querySelector('svg')
        if (svg) svg.style.transform = `rotate(${point.heading}deg)`
      }
    }
  }
  if (activePolyline && flightPath.value.length) {
    // 找到当前时间之前的所有原始点 + 当前插值点
    const pts = flightPath.value
    const absTime = pts[0].ts + currentFlightTimeMs.value
    const activePts = []
    for (const p of pts) {
      if (p.ts <= absTime) activePts.push([p.lat, p.lng])
    }
    activePts.push([point.lat, point.lng])
    activePolyline.setLatLngs(activePts)
  }
}

const onProgressChange = (val) => {
  if (!flightPath.value.length || totalDurationMs.value <= 0) return
  const wasPlaying = isPlaying.value
  pausePlay()
  currentFlightTimeMs.value = (val / 100) * totalDurationMs.value
  updateMapState()
  if (wasPlaying) startPlay()
}

const skipToStart = () => { pausePlay(); currentFlightTimeMs.value = 0; playProgress.value = 0; updateMapState() }
const skipToEnd = () => { pausePlay(); currentFlightTimeMs.value = totalDurationMs.value; playProgress.value = 100; updateMapState() }

// 倍速变化时重新校准动画起点
watch(playSpeed, () => {
  if (isPlaying.value) {
    animStartWallTime = performance.now()
    animStartFlightTime = currentFlightTimeMs.value
  }
})


// 是否全选
const isAllSelected = computed({
  get: () => flights.value.length > 0 && selectedFlights.value.length === flights.value.length,
  set: (value) => {
    selectedFlights.value = value ? flights.value.map(f => f.id) : []
  }
})

// 获取飞行记录列表
const fetchFlights = async () => {
  loading.value = true
  try {
    const res = await getFlightsList(currentPage.value, pageSize.value, searchKeyword.value)
    if (res.code === 1) {
      flights.value = res.data.flightsList || []
      total.value = res.data.pagination?.total || 0
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取飞行记录失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取飞行记录失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

// 搜索（防抖）
let searchTimer = null
const handleSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    fetchFlights()
  }, 300)
}

// 选择飞行记录
const selectFlight = async (flight) => {
  stopPlay()
  selectedFlight.value = flight
  detailLoading.value = true
  flightDetail.value = null
  flightPath.value = []
  playProgress.value = 0
  currentFlightTimeMs.value = 0
  try {
    const res = await getFlightDetail(flight.id)
    if (res.code === 1) {
      flightDetail.value = res.data
      if (res.data.flightPath && res.data.flightPath.length > 0) {
        flightPath.value = [...res.data.flightPath].sort((a, b) => a.ts - b.ts)
        await nextTick()
        renderFlightOnMap(res.data)
      } else {
        clearMap()
      }
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取详情失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取详情失败', life: 3000 })
  } finally {
    detailLoading.value = false
  }
}

// 删除单条记录
const handleDelete = async (flight) => {
  if (!confirm(`确定要删除飞行记录 #${flight.id} 吗？`)) return
  deleteLoading.value = true
  try {
    const res = await deleteFlights([flight.id])
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      fetchFlights()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedFlights.value.length === 0) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请先选择要删除的记录', life: 3000 })
    return
  }
  if (!confirm(`确定要删除选中的 ${selectedFlights.value.length} 条飞行记录吗？`)) return
  deleteLoading.value = true
  try {
    const res = await deleteFlights(selectedFlights.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      selectedFlights.value = []
      fetchFlights()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

// 格式化飞行状态
const formatStatus = (status) => {
  const map = { 0: '进行中', 1: '已完成', 2: '异常终止' }
  return map[status] ?? '未知'
}

// 状态样式
const statusClass = (status) => {
  const map = {
    0: 'bg-blue-100 text-blue-800',
    1: 'bg-green-100 text-green-800',
    2: 'bg-red-100 text-red-800'
  }
  return map[status] ?? 'bg-gray-100 text-gray-800'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return time
}

// 格式化飞行时长（秒 → 时分秒）
const formatDuration = (seconds) => {
  if (!seconds && seconds !== 0) return '-'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}时${m}分${s}秒`
  if (m > 0) return `${m}分${s}秒`
  return `${s}秒`
}

// 格式化距离（米 → 合适单位）
const formatDistance = (meters) => {
  if (!meters && meters !== 0) return '-'
  if (meters >= 1000) return `${(meters / 1000).toFixed(2)} km`
  return `${meters.toFixed(1)} m`
}

// 格式化高度/速度
const formatAltitude = (val) => {
  if (!val && val !== 0) return '-'
  return `${val.toFixed(1)} m`
}

const formatSpeed = (val) => {
  if (!val && val !== 0) return '-'
  return `${val.toFixed(1)} m/s`
}

// ==================== 轨迹导出 ====================

const generateKML = () => {
  const detail = flightDetail.value
  const pts = flightPath.value
  const coords = pts.map(p => `${p.lng},${p.lat},${p.alt || 0}`).join('\n            ')
  const homeLng = detail.homeLongitude || pts[0].lng
  const homeLat = detail.homeLatitude || pts[0].lat

  return `<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Flight #${detail.id} - ${detail.sn}</name>
    <description>飞行时长: ${formatDuration(detail.duration)}, 飞行距离: ${formatDistance(detail.distance)}, 最大高度: ${formatAltitude(detail.maxAltitude)}, 最大速度: ${formatSpeed(detail.maxSpeed)}</description>
    <Style id="flightPath">
      <LineStyle>
        <color>ffEB6325</color>
        <width>3</width>
      </LineStyle>
    </Style>
    <Style id="homePoint">
      <IconStyle>
        <color>ff0000ff</color>
        <scale>1.2</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/paddle/H.png</href></Icon>
      </IconStyle>
    </Style>
    <Placemark>
      <name>航线轨迹</name>
      <styleUrl>#flightPath</styleUrl>
      <LineString>
        <altitudeMode>absolute</altitudeMode>
        <coordinates>
            ${coords}
        </coordinates>
      </LineString>
    </Placemark>
    <Placemark>
      <name>起飞点</name>
      <styleUrl>#homePoint</styleUrl>
      <Point>
        <altitudeMode>clampToGround</altitudeMode>
        <coordinates>${homeLng},${homeLat},0</coordinates>
      </Point>
    </Placemark>
  </Document>
</kml>`
}

const generateGPX = () => {
  const detail = flightDetail.value
  const pts = flightPath.value
  const trkpts = pts.map(p => {
    const time = new Date(p.ts).toISOString()
    return `      <trkpt lat="${p.lat}" lon="${p.lng}">
        <ele>${p.alt || 0}</ele>
        <time>${time}</time>
        <speed>${p.speed || 0}</speed>
        <course>${p.heading || 0}</course>
      </trkpt>`
  }).join('\n')
  const startTime = pts.length > 0 ? new Date(pts[0].ts).toISOString() : ''

  return `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="SkyFlowTracker"
     xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>Flight #${detail.id} - ${detail.sn}</name>
    <time>${startTime}</time>
  </metadata>
  <trk>
    <name>Flight #${detail.id}</name>
    <desc>设备: ${detail.sn}, 飞行时长: ${formatDuration(detail.duration)}, 飞行距离: ${formatDistance(detail.distance)}</desc>
    <trkseg>
${trkpts}
    </trkseg>
  </trk>
</gpx>`
}

const exportTrack = (format) => {
  if (!flightDetail.value || !flightPath.value.length) return
  const detail = flightDetail.value
  const date = detail.startTime ? detail.startTime.replace(/[:\s]/g, '-').slice(0, 10) : 'unknown'
  const content = format === 'kml' ? generateKML() : generateGPX()
  const mimeType = format === 'kml' ? 'application/vnd.google-earth.kml+xml' : 'application/gpx+xml'
  const fileName = `Flight_${detail.id}_${detail.sn}_${date}.${format}`

  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)

  toast.add({ severity: 'success', summary: '成功', detail: `${format.toUpperCase()} 文件已导出`, life: 2000 })
}

onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 1) {
      isAdmin.value = res.data.role === 1
    }
  } catch (error) {}
  await fetchFlights()
  await nextTick()
  initMap()
  loadFlyZones()
})

onBeforeUnmount(() => {
  destroyMap()
})
</script>

<template>
  <div class="flex gap-4" style="height: calc(100vh - 112px);">
    <Toast />

    <!-- 左侧飞行记录列表 -->
    <div class="w-80 flex-shrink-0 bg-white rounded-2xl shadow-lg flex flex-col overflow-hidden">
      <div class="p-4 border-b border-gray-200">
        <div class="flex items-center justify-between">
          <h3 class="text-lg font-bold text-gray-900">飞行记录</h3>
          <Button v-if="isAdmin" icon="pi pi-trash" severity="danger" @click="handleBatchDelete"
            :disabled="selectedFlights.length === 0 || deleteLoading" :loading="deleteLoading"
            class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg !text-xs !px-2 !py-1"
            :label="`删除(${selectedFlights.length})`" />
        </div>
        <div v-if="isAdmin" class="flex items-center gap-2 mt-2">
          <Checkbox v-model="isAllSelected" :binary="true" />
          <span class="text-xs text-gray-500">全选</span>
        </div>
        <div class="mt-2 relative">
          <i class="pi pi-search absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 text-xs"></i>
          <input v-model="searchKeyword" @input="handleSearch" type="text" placeholder="搜索SN / 用户名"
            class="w-full pl-8 pr-3 py-1.5 text-xs border border-gray-200 rounded-lg focus:outline-none focus:border-blue-400" />
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="flex-1 flex items-center justify-center">
        <i class="pi pi-spin pi-spinner text-3xl text-blue-600"></i>
      </div>

      <!-- 列表 -->
      <div v-else class="flex-1 overflow-y-auto">
        <div v-if="flights.length === 0" class="flex flex-col items-center justify-center h-full text-gray-400">
          <i class="pi pi-send text-4xl mb-2"></i>
          <p class="text-sm">暂无飞行记录</p>
        </div>

        <div v-for="flight in flights" :key="flight.id"
          @click="selectFlight(flight)"
          class="p-4 border-b border-gray-100 cursor-pointer transition-colors hover:bg-blue-50"
          :class="{ 'bg-blue-50 border-l-4 border-l-blue-600': selectedFlight?.id === flight.id }">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <Checkbox v-if="isAdmin" v-model="selectedFlights" :value="flight.id" :binary="false"
                @click.stop />
              <span class="text-sm font-semibold text-gray-900 font-mono">{{ flight.sn || '未知设备' }}</span>
            </div>
            <div class="flex items-center gap-1">
              <span :class="['px-2 py-0.5 text-xs font-semibold rounded-full', statusClass(flight.status)]">
                {{ formatStatus(flight.status) }}
              </span>
              <button v-if="isAdmin" @click.stop="handleDelete(flight)"
                class="text-red-500 hover:text-red-700 cursor-pointer ml-1" title="删除">
                <i class="pi pi-trash text-xs"></i>
              </button>
            </div>
          </div>
          <div class="text-xs text-gray-500 space-y-1">
            <div v-if="flight.userName" class="flex items-center">
              <i class="pi pi-user mr-1.5"></i>{{ flight.userName }}
            </div>
            <div class="flex items-center">
              <i class="pi pi-calendar mr-1.5"></i>{{ flight.startTime || '-' }}
            </div>
            <div class="flex items-center gap-4">
              <span><i class="pi pi-clock mr-1"></i>{{ formatDuration(flight.duration) }}</span>
              <span><i class="pi pi-map mr-1"></i>{{ formatDistance(flight.distance) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="p-3 border-t border-gray-200 flex items-center justify-between">
        <span class="text-xs text-gray-500">共 {{ total }} 条</span>
        <div class="flex gap-1">
          <button @click="currentPage--, fetchFlights()" :disabled="currentPage === 1"
            class="w-7 h-7 flex items-center justify-center rounded text-xs transition-colors"
            :class="currentPage === 1 ? 'text-gray-400 cursor-not-allowed' : 'text-blue-600 hover:bg-blue-50 cursor-pointer'">
            <i class="pi pi-chevron-left text-xs"></i>
          </button>
          <span class="text-xs text-gray-600 leading-7">{{ currentPage }}/{{ Math.ceil(total / pageSize) || 1 }}</span>
          <button @click="currentPage++, fetchFlights()" :disabled="currentPage * pageSize >= total"
            class="w-7 h-7 flex items-center justify-center rounded text-xs transition-colors"
            :class="currentPage * pageSize >= total ? 'text-gray-400 cursor-not-allowed' : 'text-blue-600 hover:bg-blue-50 cursor-pointer'">
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
        </div>
      </div>
    </div>

    <!-- 右侧地图 + 详情 + 播放控制 -->
    <div class="flex-1 flex flex-col bg-white rounded-2xl shadow-lg overflow-hidden">
      <!-- 地图区域 -->
      <div class="flex-1 relative">
        <div ref="mapContainer" class="w-full h-full"></div>

        <!-- 加载详情遮罩 -->
        <div v-if="detailLoading" class="absolute inset-0 bg-white/70 flex items-center justify-center z-[1000]">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
        </div>

        <!-- 未选择提示 -->
        <div v-if="!selectedFlight" class="absolute inset-0 flex flex-col items-center justify-center z-[500] pointer-events-none">
          <div class="bg-white/90 rounded-2xl p-8 text-center shadow-lg">
            <i class="pi pi-play-circle text-6xl text-gray-300 mb-4"></i>
            <p class="text-gray-500 text-lg">请从左侧选择一条飞行记录</p>
            <p class="text-gray-400 text-sm mt-1">选择后即可查看航线轨迹回放</p>
          </div>
        </div>

        <!-- 飞行信息面板 -->
        <div v-if="selectedFlight && currentPoint" class="absolute top-4 left-4 z-[1000] bg-white/90 backdrop-blur rounded-xl p-3 shadow-lg text-xs space-y-1 min-w-[160px]">
          <div class="flex justify-between">
            <span class="text-gray-500">高度</span>
            <span class="text-gray-900 font-semibold">{{ currentPoint.alt != null ? currentPoint.alt.toFixed(1) + ' m' : '-' }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">速度</span>
            <span class="text-gray-900 font-semibold">{{ currentPoint.speed != null ? currentPoint.speed.toFixed(1) + ' m/s' : '-' }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500">航向</span>
            <span class="text-gray-900 font-semibold">{{ currentPoint.heading != null ? currentPoint.heading.toFixed(0) + '°' : '-' }}</span>
          </div>
        </div>

        <!-- 飞行详情面板 -->
        <div v-if="flightDetail && !detailLoading" class="absolute top-4 right-14 z-[1000] bg-white/90 backdrop-blur rounded-xl p-3 shadow-lg text-xs space-y-1 min-w-[180px]">
          <div class="flex justify-between"><span class="text-gray-500">设备SN</span><span class="text-gray-900 font-mono font-semibold">{{ flightDetail.sn || '-' }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">飞行时长</span><span class="text-gray-900 font-semibold">{{ formatDuration(flightDetail.duration) }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">最大高度</span><span class="text-gray-900 font-semibold">{{ formatAltitude(flightDetail.maxAltitude) }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">最大速度</span><span class="text-gray-900 font-semibold">{{ formatSpeed(flightDetail.maxSpeed) }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">飞行距离</span><span class="text-gray-900 font-semibold">{{ formatDistance(flightDetail.distance) }}</span></div>
          <div v-if="flightPath.length > 0" class="flex gap-2 mt-2 pt-2 border-t border-gray-200/60">
            <button @click="exportTrack('kml')" class="flex-1 flex items-center justify-center gap-1 px-2 py-1 text-xs text-blue-600 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors cursor-pointer" title="导出为 Google Earth 格式">
              <i class="pi pi-download text-[10px]"></i> KML
            </button>
            <button @click="exportTrack('gpx')" class="flex-1 flex items-center justify-center gap-1 px-2 py-1 text-xs text-green-600 bg-green-50 hover:bg-green-100 rounded-lg transition-colors cursor-pointer" title="导出为 GPX 格式">
              <i class="pi pi-download text-[10px]"></i> GPX
            </button>
          </div>
        </div>

        <!-- 禁飞区图层开关 -->
        <button @click="toggleFlyZones"
          class="absolute top-24 right-4 z-[1000] w-9 h-9 flex items-center justify-center rounded-lg shadow-lg transition-colors cursor-pointer"
          :class="showFlyZones ? 'bg-red-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-100'"
          title="切换禁飞区显示">
          <i class="pi pi-shield text-sm"></i>
        </button>
      </div>

      <!-- 底部播放控制栏 -->
      <div v-if="selectedFlight && flightPath.length > 0" class="border-t border-gray-200 bg-white px-6 py-3">
        <div class="mb-3">
          <Slider v-model="playProgress" :min="0" :max="100" :step="0.1" @change="onProgressChange" class="w-full" />
        </div>
        <div class="flex items-center justify-between">
          <Dropdown v-model="playSpeed" :options="speedOptions" optionLabel="label" optionValue="value" class="w-20 text-xs" />
          <div class="flex items-center gap-2">
            <button @click="skipToStart" class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 transition-colors cursor-pointer text-gray-600">
              <i class="pi pi-step-backward"></i>
            </button>
            <button @click="togglePlay" class="w-10 h-10 flex items-center justify-center rounded-full bg-blue-600 hover:bg-blue-700 transition-colors cursor-pointer text-white shadow-md">
              <i :class="isPlaying ? 'pi pi-pause' : 'pi pi-play'" class="text-lg"></i>
            </button>
            <button @click="skipToEnd" class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 transition-colors cursor-pointer text-gray-600">
              <i class="pi pi-step-forward"></i>
            </button>
          </div>
          <span class="text-sm text-gray-600 font-mono min-w-[100px] text-right">
            {{ formatPlayTime(currentPlayTime) }} / {{ formatPlayTime(totalDuration) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 复选框样式 */
:deep(.p-checkbox) {
  width: 1.25rem;
  height: 1.25rem;
  min-width: 1.25rem;
  max-width: 1.25rem;
  flex-shrink: 0;
}

:deep(.p-checkbox .p-checkbox-box) {
  width: 1.25rem;
  height: 1.25rem;
  background-color: #ffffff;
  border: 2px solid #d1d5db;
  cursor: pointer;
}

:deep(.p-checkbox .p-checkbox-box.p-highlight) {
  background-color: #ffffff;
  border-color: #2563eb;
}

:deep(.p-checkbox .p-checkbox-box .p-checkbox-icon) {
  color: #2563eb;
  font-size: 0.875rem;
}

/* 下拉框样式 */
:deep(.p-dropdown) {
  background-color: #ffffff !important;
  border: 1px solid #E5E7EB !important;
  border-radius: 8px !important;
  font-size: 12px !important;
}

/* 起飞点 H 标记 */
:deep(.home-marker) {
  background: none !important;
  border: none !important;
}

:deep(.home-icon) {
  width: 32px;
  height: 32px;
  background: #F59E0B;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  font-size: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  border: 2px solid white;
}

/* 无人机图标 */
:deep(.drone-marker) {
  background: none !important;
  border: none !important;
}

:deep(.drone-icon) {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}

:deep(.drone-icon svg) {
  transition: transform 0.3s ease;
}

/* 进度条样式 */
:deep(.p-slider) {
  height: 6px;
  background: #E5E7EB;
  border-radius: 3px;
}

:deep(.p-slider .p-slider-range) {
  background: #2563EB;
  border-radius: 3px;
}

:deep(.p-slider .p-slider-handle) {
  width: 16px;
  height: 16px;
  background: #2563EB;
  border: 2px solid white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  margin-top: -5px;
  cursor: pointer;
}

:deep(.p-slider .p-slider-handle:hover) {
  background: #1D4ED8;
  transform: scale(1.2);
}
</style>
