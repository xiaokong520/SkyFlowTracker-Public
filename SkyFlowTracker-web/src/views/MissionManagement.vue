<script setup>
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import { useToast } from 'primevue/usetoast'
import { checkToken, getUsersList } from '../api/auth'
import { getMissionsList, getMissionDetail, createMission, updateMission, deleteMissions, assignMission } from '../api/missions'
import { getAllEnabledFlyZones, createFlyZone, deleteFlyZones, getFlyZonesList } from '../api/flyZones'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet-draw'
import 'leaflet-draw/dist/leaflet.draw.css'
import { OpenStreetMapProvider } from 'leaflet-geosearch'

const toast = useToast()
const isAdmin = ref(false)
const loading = ref(true)

// ==================== Tab 切换 ====================
const activeTab = ref('missions') // 'missions' | 'flyZones'

// ==================== 航线任务数据 ====================
const missions = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchKeyword = ref('')
const selectedMission = ref(null)
const missionDetail = ref(null)
const detailLoading = ref(false)
const selectedMissions = ref([])

// ==================== 禁飞区数据 ====================
const flyZones = ref([])
const flyZonesList = ref([])
const flyZonesPage = ref(1)
const flyZonesTotal = ref(0)
const flyZonesFilter = ref(null) // null=全部, 0/1/2/3=按类别筛选

// ==================== 地图 ====================
const mapContainer = ref(null)
let map = null
let drawControl = null
let drawnItems = null
const isDrawingMode = ref(false)
const drawingType = ref('') // 'waypoint' | 'flyZoneCircle' | 'flyZonePolygon'

// 航点数据
const waypoints = ref([])
let waypointMarkers = []
let waypointPolyline = null
let conflictPolylines = []

// 禁飞区图层
let flyZoneLayers = []
const showFlyZones = ref(true)

// POI 搜索
const geoProvider = new OpenStreetMapProvider()
const searchQuery = ref('')
const searchResults = ref([])
const searchLoading = ref(false)
let searchMarker = null
let searchDebounceTimer = null

// 禁飞区颜色配置
const flyZoneColors = {
  0: { stroke: '#FFD700', fill: 'rgba(255, 215, 0, 0.15)', name: '警告区' },
  1: { stroke: '#FF8C00', fill: 'rgba(255, 140, 0, 0.15)', name: '增强警告区' },
  2: { stroke: '#4169E1', fill: 'rgba(65, 105, 225, 0.15)', name: '授权区' },
  3: { stroke: '#DC143C', fill: 'rgba(220, 20, 60, 0.15)', name: '禁飞区' }
}

// 状态映射
const statusMap = { 0: '草稿', 1: '已发布', 2: '已分派', 3: '执行中', 4: '已完成', 5: '已取消' }
const statusClassMap = {
  0: 'bg-gray-100 text-gray-800',
  1: 'bg-blue-100 text-blue-800',
  2: 'bg-purple-100 text-purple-800',
  3: 'bg-yellow-100 text-yellow-800',
  4: 'bg-green-100 text-green-800',
  5: 'bg-red-100 text-red-800'
}

// ==================== 对话框 ====================
const showCreateDialog = ref(false)
const showAssignDialog = ref(false)
const showFlyZoneCreateDialog = ref(false)
const missionForm = ref({ name: '', description: '' })
const assignForm = ref({ missionId: null, assigneeId: null })
const isReassign = ref(false)
const flyZoneForm = ref({ name: '', category: 3, description: '' })
const usersList = ref([])

// ==================== 地图初始化 ====================
const initMap = () => {
  if (!mapContainer.value || map) return
  map = L.map(mapContainer.value, {
    center: [24.5, 118.1],
    zoom: 13,
    zoomControl: false
  })
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap',
    maxZoom: 19
  }).addTo(map)
  L.control.zoom({ position: 'topright' }).addTo(map)

  // leaflet-draw 图层
  drawnItems = new L.FeatureGroup()
  map.addLayer(drawnItems)

  // 点击地图添加航点（仅在绘制模式下）
  map.on('click', onMapClick)
}

const destroyMap = () => {
  exitDrawingMode()
  if (map) { map.remove(); map = null }
}

// ==================== 禁飞区图层渲染 ====================
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
  // 清除旧图层
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

// 切换禁飞区显示
const toggleFlyZones = () => {
  showFlyZones.value = !showFlyZones.value
  flyZoneLayers.forEach(l => {
    if (showFlyZones.value) l.addTo(map)
    else map.removeLayer(l)
  })
}

// ==================== POI 搜索 ====================
const searchPOI = () => {
  clearTimeout(searchDebounceTimer)
  const query = searchQuery.value.trim()
  if (!query) {
    searchResults.value = []
    return
  }
  searchDebounceTimer = setTimeout(async () => {
    searchLoading.value = true
    try {
      const results = await geoProvider.search({ query })
      searchResults.value = results.slice(0, 6)
    } catch (e) {
      console.error('POI 搜索失败:', e)
      searchResults.value = []
    } finally {
      searchLoading.value = false
    }
  }, 300)
}

const selectSearchResult = (result) => {
  if (!map) return
  if (searchMarker) { map.removeLayer(searchMarker); searchMarker = null }
  map.flyTo([result.y, result.x], 16)
  searchMarker = L.marker([result.y, result.x]).addTo(map)
    .bindPopup(`<b>${result.label}</b>`).openPopup()
  searchResults.value = []
}

const clearSearch = () => {
  searchQuery.value = ''
  searchResults.value = []
  if (searchMarker && map) { map.removeLayer(searchMarker); searchMarker = null }
}

// ==================== 禁飞区冲突检测 ====================

// 点是否在圆形区域内
const isPointInCircle = (lat, lng, zone) => {
  if (zone.shape !== 0 || !zone.centerLat || !zone.centerLng) return false
  const dist = map.distance([lat, lng], [zone.centerLat, zone.centerLng])
  return dist <= (zone.radius || 0)
}

// 点是否在多边形内（射线法）
const isPointInPolygon = (lat, lng, zone) => {
  if (zone.shape !== 1 || !zone.polygonPoints) return false
  const pts = typeof zone.polygonPoints === 'string' ? JSON.parse(zone.polygonPoints) : zone.polygonPoints
  if (!pts || pts.length < 3) return false

  let inside = false
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const xi = pts[i].lat, yi = pts[i].lng
    const xj = pts[j].lat, yj = pts[j].lng
    const intersect = ((yi > lng) !== (yj > lng)) && (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi)
    if (intersect) inside = !inside
  }
  return inside
}

// 检测航点是否在禁飞区内
const checkPointConflict = (lat, lng) => {
  const conflicts = []
  for (const zone of flyZones.value) {
    let inZone = false
    if (zone.shape === 0) inZone = isPointInCircle(lat, lng, zone)
    else if (zone.shape === 1) inZone = isPointInPolygon(lat, lng, zone)
    if (inZone) conflicts.push(zone)
  }
  return conflicts
}

// 检测航段是否穿越禁飞区/授权区（简化：采样检测）
const checkSegmentConflict = (lat1, lng1, lat2, lng2) => {
  const steps = 10
  let hasRestricted = false
  let hasAuthorization = false
  for (let i = 1; i < steps; i++) {
    const t = i / steps
    const lat = lat1 + (lat2 - lat1) * t
    const lng = lng1 + (lng2 - lng1) * t
    const conflicts = checkPointConflict(lat, lng)
    if (conflicts.some(z => z.category === 3)) hasRestricted = true
    if (conflicts.some(z => z.category === 2)) hasAuthorization = true
  }
  return { restricted: hasRestricted, authorization: hasAuthorization }
}

// ==================== 航线绘制 ====================
const enterWaypointDrawing = () => {
  isDrawingMode.value = true
  drawingType.value = 'waypoint'
  waypoints.value = []
  clearWaypointLayers()
  toast.add({ severity: 'info', summary: '绘制模式', detail: '点击地图添加航点，完成后点击保存', life: 3000 })
}

const exitDrawingMode = () => {
  isDrawingMode.value = false
  drawingType.value = ''
  clearWaypointLayers()
  clearConflictLines()
  if (drawnItems) drawnItems.clearLayers()
}

const clearWaypointLayers = () => {
  waypointMarkers.forEach(m => map && map.removeLayer(m))
  waypointMarkers = []
  if (waypointPolyline && map) { map.removeLayer(waypointPolyline); waypointPolyline = null }
}

const clearConflictLines = () => {
  conflictPolylines.forEach(l => map && map.removeLayer(l))
  conflictPolylines = []
}

const onMapClick = (e) => {
  if (!isDrawingMode.value || drawingType.value !== 'waypoint') return
  const { lat, lng } = e.latlng

  // 禁飞区冲突检测
  const conflicts = checkPointConflict(lat, lng)
  const restricted = conflicts.filter(z => z.category === 3) // RESTRICTED
  const authorization = conflicts.filter(z => z.category === 2) // AUTHORIZATION
  const enhanced = conflicts.filter(z => z.category === 1) // ENHANCED_WARNING
  const warning = conflicts.filter(z => z.category === 0) // WARNING

  if (restricted.length > 0) {
    toast.add({ severity: 'error', summary: '禁飞区', detail: `该位置位于禁飞区"${restricted[0].name}"内，无法放置航点`, life: 4000 })
    return
  }
  if (authorization.length > 0) {
    toast.add({ severity: 'warn', summary: '授权区', detail: `该位置位于授权飞行区"${authorization[0].name}"${authorization[0].maxAltitude ? '，限高' + authorization[0].maxAltitude + 'm' : ''}，需申请授权后方可飞行`, life: 4000 })
  }
  if (enhanced.length > 0) {
    toast.add({ severity: 'warn', summary: '增强警告区', detail: `该位置位于增强警告区域"${enhanced[0].name}"，请注意飞行安全`, life: 3000 })
  }
  if (warning.length > 0) {
    toast.add({ severity: 'info', summary: '警告区', detail: `该位置位于警告区域"${warning[0].name}"`, life: 2000 })
  }

  // 检测航段穿越
  if (waypoints.value.length > 0) {
    const last = waypoints.value[waypoints.value.length - 1]
    const segConflict = checkSegmentConflict(last.lat, last.lng, lat, lng)
    if (segConflict.restricted) {
      toast.add({ severity: 'error', summary: '航线冲突', detail: '航线经过禁飞区，请调整航点位置', life: 4000 })
      const conflictLine = L.polyline([[last.lat, last.lng], [lat, lng]], {
        color: '#DC143C', weight: 3, dashArray: '8, 4', opacity: 0.8
      }).addTo(map)
      conflictPolylines.push(conflictLine)
    } else if (segConflict.authorization) {
      toast.add({ severity: 'warn', summary: '航线提醒', detail: '航线经过授权飞行区，需申请授权后方可飞行', life: 4000 })
      const conflictLine = L.polyline([[last.lat, last.lng], [lat, lng]], {
        color: '#4169E1', weight: 3, dashArray: '8, 4', opacity: 0.8
      }).addTo(map)
      conflictPolylines.push(conflictLine)
    }
  }

  // 添加航点
  const seq = waypoints.value.length + 1
  waypoints.value.push({ seq, lat, lng, alt: 50, speed: 5, heading: 0, actions: [], hoverTime: 0 })

  // 添加标记
  const marker = L.marker([lat, lng], {
    icon: L.divIcon({
      className: 'waypoint-marker',
      html: `<div class="waypoint-icon">${seq}</div>`,
      iconSize: [28, 28], iconAnchor: [14, 14]
    }),
    draggable: true
  }).addTo(map)

  marker.on('dragend', (e) => {
    const idx = seq - 1
    const pos = e.target.getLatLng()
    // 检测拖拽目标是否在禁飞区
    const dragConflicts = checkPointConflict(pos.lat, pos.lng)
    const dragRestricted = dragConflicts.filter(z => z.category === 3)
    if (dragRestricted.length > 0) {
      toast.add({ severity: 'error', summary: '禁飞区', detail: '无法将航点移动到禁飞区内', life: 3000 })
      e.target.setLatLng([waypoints.value[idx].lat, waypoints.value[idx].lng])
      return
    }
    const dragAuthorization = dragConflicts.filter(z => z.category === 2)
    if (dragAuthorization.length > 0) {
      toast.add({ severity: 'warn', summary: '授权区', detail: `该位置位于授权飞行区"${dragAuthorization[0].name}"，需申请授权后方可飞行`, life: 3000 })
    }
    waypoints.value[idx].lat = pos.lat
    waypoints.value[idx].lng = pos.lng
    updateWaypointPolyline()
  })

  waypointMarkers.push(marker)
  updateWaypointPolyline()
}

const updateWaypointPolyline = () => {
  if (!map) return
  if (waypointPolyline) { map.removeLayer(waypointPolyline) }
  clearConflictLines()
  if (waypoints.value.length >= 2) {
    const pts = waypoints.value.map(w => [w.lat, w.lng])
    waypointPolyline = L.polyline(pts, { color: '#2563EB', weight: 3, opacity: 0.9 }).addTo(map)

    // 检测每段航线是否穿越禁飞区
    for (let i = 0; i < waypoints.value.length - 1; i++) {
      const a = waypoints.value[i]
      const b = waypoints.value[i + 1]
      if (checkSegmentConflict(a.lat, a.lng, b.lat, b.lng)) {
        const conflictLine = L.polyline([[a.lat, a.lng], [b.lat, b.lng]], {
          color: '#DC143C', weight: 3, dashArray: '8, 4', opacity: 0.8
        }).addTo(map)
        conflictPolylines.push(conflictLine)
      }
    }
  }
}

// 撤销最后一个航点
const undoLastWaypoint = () => {
  if (waypoints.value.length === 0) return
  waypoints.value.pop()
  const marker = waypointMarkers.pop()
  if (marker && map) map.removeLayer(marker)
  updateWaypointPolyline()
}

// ==================== 航线任务 CRUD ====================
const fetchMissions = async () => {
  loading.value = true
  try {
    const res = await getMissionsList(currentPage.value, pageSize.value, searchKeyword.value)
    if (res.code === 1) {
      missions.value = res.data.missionsList || []
      total.value = res.data.pagination?.total || 0
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取航线任务失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

let searchTimer = null
const handleSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { currentPage.value = 1; fetchMissions() }, 300)
}

const selectMission = async (mission) => {
  selectedMission.value = mission
  detailLoading.value = true
  try {
    const res = await getMissionDetail(mission.id)
    if (res.code === 1) {
      missionDetail.value = res.data
      renderMissionOnMap(res.data)
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取详情失败', life: 3000 })
  } finally {
    detailLoading.value = false
  }
}

const renderMissionOnMap = (detail) => {
  if (!map) return
  clearWaypointLayers()
  clearConflictLines()

  const wps = detail.waypoints || []
  if (wps.length === 0) return

  wps.forEach((wp, idx) => {
    const marker = L.marker([wp.lat, wp.lng], {
      icon: L.divIcon({
        className: 'waypoint-marker',
        html: `<div class="waypoint-icon">${idx + 1}</div>`,
        iconSize: [28, 28], iconAnchor: [14, 14]
      })
    }).addTo(map)
    marker.bindPopup(`<b>航点 ${idx + 1}</b><br>高度: ${wp.alt}m<br>速度: ${wp.speed}m/s`)
    waypointMarkers.push(marker)
  })

  if (wps.length >= 2) {
    const pts = wps.map(w => [w.lat, w.lng])
    waypointPolyline = L.polyline(pts, { color: '#2563EB', weight: 3, opacity: 0.9 }).addTo(map)
    map.fitBounds(waypointPolyline.getBounds().pad(0.2))
  } else {
    map.setView([wps[0].lat, wps[0].lng], 15)
  }
}

// 保存航线任务
const saveMission = async () => {
  if (!missionForm.value.name) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入任务名称', life: 3000 })
    return
  }
  if (waypoints.value.length < 2) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请至少添加2个航点', life: 3000 })
    return
  }

  // 计算总距离
  let totalDist = 0
  for (let i = 0; i < waypoints.value.length - 1; i++) {
    totalDist += map.distance(
      [waypoints.value[i].lat, waypoints.value[i].lng],
      [waypoints.value[i + 1].lat, waypoints.value[i + 1].lng]
    )
  }

  try {
    const data = {
      name: missionForm.value.name,
      description: missionForm.value.description,
      waypoints: JSON.stringify(waypoints.value),
      totalDistance: Math.round(totalDist),
      estimatedTime: Math.round(totalDist / 5) // 按5m/s估算
    }
    const res = await createMission(data)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '航线任务创建成功', life: 3000 })
      showCreateDialog.value = false
      exitDrawingMode()
      missionForm.value = { name: '', description: '' }
      fetchMissions()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '创建失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '创建失败', life: 3000 })
  }
}

// 删除航线任务
const handleDeleteMission = async (mission) => {
  if (!confirm(`确定要删除航线任务"${mission.name}"吗？`)) return
  try {
    const res = await deleteMissions([mission.id])
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      if (selectedMission.value?.id === mission.id) {
        selectedMission.value = null
        missionDetail.value = null
        clearWaypointLayers()
      }
      fetchMissions()
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedMissions.value.length === 0) return
  if (!confirm(`确定要删除选中的 ${selectedMissions.value.length} 条航线任务吗？`)) return
  try {
    const res = await deleteMissions(selectedMissions.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      selectedMissions.value = []
      fetchMissions()
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  }
}

// ==================== 任务分派 ====================
const openAssignDialog = async (mission, reassign = false) => {
  isReassign.value = reassign
  assignForm.value = { missionId: mission.id, assigneeId: null }
  try {
    const res = await getUsersList(1, 100)
    if (res.code === 1) {
      let list = (res.data.userInfoList || []).map(u => ({ label: u.userName || u.email, value: u.userId }))
      if (reassign && mission.assigneeId) {
        list = list.filter(u => u.value !== mission.assigneeId)
      }
      usersList.value = list
    }
  } catch (e) {}
  showAssignDialog.value = true
}

const confirmAssign = async () => {
  if (!assignForm.value.assigneeId) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请选择执行人', life: 3000 })
    return
  }
  const actionLabel = isReassign.value ? '转派' : '分派'
  try {
    const res = await assignMission(assignForm.value.missionId, assignForm.value.assigneeId)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: `${actionLabel}成功`, life: 3000 })
      showAssignDialog.value = false
      fetchMissions()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || `${actionLabel}失败`, life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: `${actionLabel}失败`, life: 3000 })
  }
}

// ==================== 禁飞区管理（管理员手动创建） ====================
let tempDrawLayer = null

const enterFlyZoneDrawing = (type) => {
  drawingType.value = type === 'circle' ? 'flyZoneCircle' : 'flyZonePolygon'
  isDrawingMode.value = true

  if (drawControl) { map.removeControl(drawControl) }

  const drawOptions = {
    draw: {
      polyline: false, marker: false, circlemarker: false, rectangle: false,
      circle: type === 'circle' ? { shapeOptions: { color: '#DC143C', fillOpacity: 0.15 } } : false,
      polygon: type === 'polygon' ? { shapeOptions: { color: '#DC143C', fillOpacity: 0.15 } } : false
    },
    edit: { featureGroup: drawnItems }
  }
  drawControl = new L.Control.Draw(drawOptions)
  map.addControl(drawControl)

  map.once(L.Draw.Event.CREATED, (e) => {
    tempDrawLayer = e.layer
    drawnItems.addLayer(tempDrawLayer)
    map.removeControl(drawControl)
    drawControl = null

    // 提取几何数据
    if (type === 'circle') {
      const center = tempDrawLayer.getLatLng()
      const radius = tempDrawLayer.getRadius()
      flyZoneForm.value.centerLat = center.lat
      flyZoneForm.value.centerLng = center.lng
      flyZoneForm.value.radius = Math.round(radius)
      flyZoneForm.value.shape = 0
    } else {
      const latlngs = tempDrawLayer.getLatLngs()[0]
      flyZoneForm.value.polygonPoints = JSON.stringify(latlngs.map(ll => ({ lat: ll.lat, lng: ll.lng })))
      flyZoneForm.value.shape = 1
    }
    showFlyZoneCreateDialog.value = true
  })

  toast.add({ severity: 'info', summary: '绘制模式', detail: type === 'circle' ? '在地图上绘制圆形禁飞区' : '在地图上绘制多边形禁飞区', life: 3000 })
}

const saveFlyZone = async () => {
  if (!flyZoneForm.value.name) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入区域名称', life: 3000 })
    return
  }
  try {
    const data = { ...flyZoneForm.value }
    const res = await createFlyZone(data)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '禁飞区创建成功', life: 3000 })
      showFlyZoneCreateDialog.value = false
      flyZoneForm.value = { name: '', category: 3, description: '' }
      if (tempDrawLayer) { drawnItems.removeLayer(tempDrawLayer); tempDrawLayer = null }
      isDrawingMode.value = false
      drawingType.value = ''
      loadFlyZones()
      fetchFlyZonesList()
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '创建失败', life: 3000 })
  }
}

const fetchFlyZonesList = async () => {
  try {
    const res = await getFlyZonesList(flyZonesPage.value, 20, '', flyZonesFilter.value)
    if (res.code === 1) {
      flyZonesList.value = res.data.flyZonesList || []
      flyZonesTotal.value = res.data.pagination?.total || 0
    }
  } catch (e) {}
}

const handleDeleteFlyZone = async (zone) => {
  if (!confirm(`确定要删除禁飞区"${zone.name}"吗？`)) return
  try {
    const res = await deleteFlyZones([zone.id])
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      fetchFlyZonesList()
      loadFlyZones()
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  }
}

// ==================== 工具函数 ====================
const formatDistance = (m) => {
  if (!m && m !== 0) return '-'
  return m >= 1000 ? `${(m / 1000).toFixed(2)} km` : `${Math.round(m)} m`
}

const formatDuration = (s) => {
  if (!s && s !== 0) return '-'
  const m = Math.floor(s / 60)
  return m > 0 ? `${m}分${s % 60}秒` : `${s}秒`
}

const isAllSelected = computed({
  get: () => missions.value.length > 0 && selectedMissions.value.length === missions.value.length,
  set: (v) => { selectedMissions.value = v ? missions.value.map(m => m.id) : [] }
})

const categoryOptions = [
  { label: '警告区 (WARNING)', value: 0 },
  { label: '增强警告 (ENHANCED)', value: 1 },
  { label: '授权区 (AUTHORIZATION)', value: 2 },
  { label: '禁飞区 (RESTRICTED)', value: 3 }
]

const filterCategoryOptions = [
  { label: '全部', value: null },
  { label: '警告区', value: 0 },
  { label: '增强警告区', value: 1 },
  { label: '授权区', value: 2 },
  { label: '禁飞区', value: 3 }
]

const onFilterChange = () => {
  flyZonesPage.value = 1
  fetchFlyZonesList()
}

// ==================== 生命周期 ====================
onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 1) isAdmin.value = res.data.role === 1
  } catch (e) {}
  await fetchMissions()
  await nextTick()
  initMap()
  loadFlyZones()
  if (isAdmin.value) fetchFlyZonesList()
})

onBeforeUnmount(() => { destroyMap() })
</script>

<template>
  <div class="flex gap-4" style="height: calc(100vh - 112px);">
    <Toast />

    <!-- 左侧面板 -->
    <div class="w-80 flex-shrink-0 bg-white rounded-2xl shadow-lg flex flex-col overflow-hidden">
      <!-- Tab 切换 -->
      <div class="flex border-b border-gray-200">
        <button @click="activeTab = 'missions'"
          class="flex-1 py-3 text-sm font-semibold text-center transition-colors cursor-pointer"
          :class="activeTab === 'missions' ? 'text-blue-600 border-b-2 border-blue-600' : 'text-gray-500 hover:text-gray-700'">
          航线任务
        </button>
        <button v-if="isAdmin" @click="activeTab = 'flyZones'; fetchFlyZonesList()"
          class="flex-1 py-3 text-sm font-semibold text-center transition-colors cursor-pointer"
          :class="activeTab === 'flyZones' ? 'text-blue-600 border-b-2 border-blue-600' : 'text-gray-500 hover:text-gray-700'">
          禁飞区管理
        </button>
      </div>

      <!-- 航线任务 Tab -->
      <template v-if="activeTab === 'missions'">
        <div class="p-4 border-b border-gray-200">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-bold text-gray-900">航线任务</h3>
            <div class="flex gap-1" v-if="isAdmin">
              <Button icon="pi pi-plus" severity="info" @click="enterWaypointDrawing"
                class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg !text-xs !px-2 !py-1"
                label="创建" :disabled="isDrawingMode" />
              <Button icon="pi pi-trash" severity="danger" @click="handleBatchDelete"
                :disabled="selectedMissions.length === 0"
                class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg !text-xs !px-2 !py-1"
                :label="`删除(${selectedMissions.length})`" />
            </div>
          </div>
          <div v-if="isAdmin" class="flex items-center gap-2 mt-2">
            <Checkbox v-model="isAllSelected" :binary="true" />
            <span class="text-xs text-gray-500">全选</span>
          </div>
          <div class="mt-2 relative">
            <i class="pi pi-search absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 text-xs"></i>
            <input v-model="searchKeyword" @input="handleSearch" type="text" placeholder="搜索任务名称"
              class="w-full pl-8 pr-3 py-1.5 text-xs border border-gray-200 rounded-lg focus:outline-none focus:border-blue-400" />
          </div>
        </div>

        <div v-if="loading" class="flex-1 flex items-center justify-center">
          <i class="pi pi-spin pi-spinner text-3xl text-blue-600"></i>
        </div>

        <div v-else class="flex-1 overflow-y-auto">
          <div v-if="missions.length === 0" class="flex flex-col items-center justify-center h-full text-gray-400">
            <i class="pi pi-map text-4xl mb-2"></i>
            <p class="text-sm">暂无航线任务</p>
          </div>

          <div v-for="mission in missions" :key="mission.id"
            @click="selectMission(mission)"
            class="p-4 border-b border-gray-100 cursor-pointer transition-colors hover:bg-blue-50"
            :class="{ 'bg-blue-50 border-l-4 border-l-blue-600': selectedMission?.id === mission.id }">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <Checkbox v-if="isAdmin" v-model="selectedMissions" :value="mission.id" :binary="false" @click.stop />
                <span class="text-sm font-semibold text-gray-900">{{ mission.name }}</span>
              </div>
              <span :class="['px-2 py-0.5 text-xs font-semibold rounded-full', statusClassMap[mission.status]]">
                {{ statusMap[mission.status] }}
              </span>
            </div>
            <div class="text-xs text-gray-500 space-y-1">
              <div v-if="mission.creatorName" class="flex items-center">
                <i class="pi pi-user mr-1.5"></i>创建: {{ mission.creatorName }}
              </div>
              <div v-if="mission.assigneeName" class="flex items-center">
                <i class="pi pi-user-edit mr-1.5"></i>执行: {{ mission.assigneeName }}
              </div>
              <div class="flex items-center gap-4">
                <span><i class="pi pi-map mr-1"></i>{{ formatDistance(mission.totalDistance) }}</span>
                <span><i class="pi pi-clock mr-1"></i>{{ formatDuration(mission.estimatedTime) }}</span>
              </div>
              <div v-if="mission.createTime" class="flex items-center">
                <i class="pi pi-calendar mr-1.5"></i>{{ new Date(mission.createTime).toLocaleString() }}
              </div>
            </div>
            <!-- 管理员操作 -->
            <div v-if="isAdmin" class="flex gap-1 mt-2" @click.stop>
              <button v-if="mission.status === 0 || mission.status === 1" @click="openAssignDialog(mission)"
                class="px-2 py-0.5 text-xs text-purple-600 bg-purple-50 hover:bg-purple-100 rounded cursor-pointer">
                分派
              </button>
              <button v-if="mission.status === 2" @click="openAssignDialog(mission, true)"
                class="px-2 py-0.5 text-xs text-orange-600 bg-orange-50 hover:bg-orange-100 rounded cursor-pointer">
                转派
              </button>
              <button @click="handleDeleteMission(mission)"
                class="px-2 py-0.5 text-xs text-red-600 bg-red-50 hover:bg-red-100 rounded cursor-pointer">
                删除
              </button>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="p-3 border-t border-gray-200 flex items-center justify-between">
          <span class="text-xs text-gray-500">共 {{ total }} 条</span>
          <div class="flex gap-1">
            <button @click="currentPage--, fetchMissions()" :disabled="currentPage === 1"
              class="w-7 h-7 flex items-center justify-center rounded text-xs transition-colors"
              :class="currentPage === 1 ? 'text-gray-400 cursor-not-allowed' : 'text-blue-600 hover:bg-blue-50 cursor-pointer'">
              <i class="pi pi-chevron-left text-xs"></i>
            </button>
            <span class="text-xs text-gray-600 leading-7">{{ currentPage }}/{{ Math.ceil(total / pageSize) || 1 }}</span>
            <button @click="currentPage++, fetchMissions()" :disabled="currentPage * pageSize >= total"
              class="w-7 h-7 flex items-center justify-center rounded text-xs transition-colors"
              :class="currentPage * pageSize >= total ? 'text-gray-400 cursor-not-allowed' : 'text-blue-600 hover:bg-blue-50 cursor-pointer'">
              <i class="pi pi-chevron-right text-xs"></i>
            </button>
          </div>
        </div>
      </template>

      <!-- 禁飞区管理 Tab -->
      <template v-if="activeTab === 'flyZones' && isAdmin">
        <div class="p-4 border-b border-gray-200">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-bold text-gray-900">禁飞区管理</h3>
          </div>
          <div class="flex gap-1 mt-2">
            <Dropdown v-model="flyZonesFilter" :options="filterCategoryOptions" optionLabel="label" optionValue="value"
              @change="onFilterChange" class="!text-xs flex-1" />
            <Button icon="pi pi-circle" @click="enterFlyZoneDrawing('circle')" :disabled="isDrawingMode"
              class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg !text-xs !px-2 !py-1"
              label="圆形" />
            <Button icon="pi pi-stop" @click="enterFlyZoneDrawing('polygon')" :disabled="isDrawingMode"
              class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg !text-xs !px-2 !py-1"
              label="多边形" />
          </div>
        </div>

        <div class="flex-1 overflow-y-auto">
          <div v-if="flyZonesList.length === 0" class="flex flex-col items-center justify-center h-full text-gray-400">
            <i class="pi pi-shield text-4xl mb-2"></i>
            <p class="text-sm">暂无禁飞区数据</p>
          </div>

          <div v-for="zone in flyZonesList" :key="zone.id" class="p-4 border-b border-gray-100">
            <div class="flex items-center justify-between mb-1">
              <span class="text-sm font-semibold text-gray-900">{{ zone.name }}</span>
              <div class="flex items-center gap-1">
                <span class="w-3 h-3 rounded-full inline-block" :style="{ backgroundColor: flyZoneColors[zone.category]?.stroke }"></span>
                <span class="text-xs text-gray-500">{{ flyZoneColors[zone.category]?.name }}</span>
              </div>
            </div>
            <div class="text-xs text-gray-500">
              <span>{{ zone.shape === 0 ? '圆形' : '多边形' }}</span>
              <span v-if="zone.source === 0" class="ml-2 text-blue-500">DJI同步</span>
              <span v-else class="ml-2 text-green-500">手动创建</span>
            </div>
            <div class="flex gap-1 mt-1">
              <button @click="handleDeleteFlyZone(zone)"
                class="px-2 py-0.5 text-xs text-red-600 bg-red-50 hover:bg-red-100 rounded cursor-pointer">
                删除
              </button>
            </div>
          </div>
        </div>

        <div class="p-3 border-t border-gray-200 flex items-center justify-between">
          <span class="text-xs text-gray-500">共 {{ flyZonesTotal }} 个禁飞/限飞区域</span>
          <div class="flex gap-1">
            <button @click="flyZonesPage--, fetchFlyZonesList()" :disabled="flyZonesPage === 1"
              class="w-7 h-7 flex items-center justify-center rounded text-xs transition-colors"
              :class="flyZonesPage === 1 ? 'text-gray-400 cursor-not-allowed' : 'text-blue-600 hover:bg-blue-50 cursor-pointer'">
              <i class="pi pi-chevron-left text-xs"></i>
            </button>
            <span class="text-xs text-gray-600 leading-7">{{ flyZonesPage }}/{{ Math.ceil(flyZonesTotal / 20) || 1 }}</span>
            <button @click="flyZonesPage++, fetchFlyZonesList()" :disabled="flyZonesPage * 20 >= flyZonesTotal"
              class="w-7 h-7 flex items-center justify-center rounded text-xs transition-colors"
              :class="flyZonesPage * 20 >= flyZonesTotal ? 'text-gray-400 cursor-not-allowed' : 'text-blue-600 hover:bg-blue-50 cursor-pointer'">
              <i class="pi pi-chevron-right text-xs"></i>
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- 右侧地图 -->
    <div class="flex-1 flex flex-col bg-white rounded-2xl shadow-lg overflow-hidden">
      <div class="flex-1 relative">
        <div ref="mapContainer" class="w-full h-full"></div>

        <div v-if="detailLoading" class="absolute inset-0 bg-white/70 flex items-center justify-center z-[1000]">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
        </div>

        <!-- POI 搜索框 -->
        <div v-if="!isDrawingMode" class="absolute top-4 left-4 z-[1000] w-72">
          <div class="relative">
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm"></i>
            <input v-model="searchQuery" @input="searchPOI" @keydown.enter.prevent="searchPOI"
              type="text" placeholder="搜索地点..."
              class="w-full pl-9 pr-8 py-2 text-sm bg-white/95 backdrop-blur border border-gray-200 rounded-lg shadow-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" />
            <button v-if="searchQuery" @click="clearSearch"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 cursor-pointer">
              <i class="pi pi-times text-sm"></i>
            </button>
          </div>
          <div v-if="searchLoading" class="mt-1 bg-white/95 backdrop-blur rounded-lg shadow-lg p-3 text-center">
            <i class="pi pi-spin pi-spinner text-sm text-blue-600 mr-1"></i>
            <span class="text-xs text-gray-500">搜索中...</span>
          </div>
          <div v-else-if="searchResults.length > 0" class="mt-1 bg-white/95 backdrop-blur rounded-lg shadow-lg overflow-hidden max-h-60 overflow-y-auto">
            <div v-for="(result, idx) in searchResults" :key="idx"
              @click="selectSearchResult(result)"
              class="px-3 py-2 text-xs text-gray-700 hover:bg-blue-50 cursor-pointer border-b border-gray-100 last:border-b-0 transition-colors">
              <i class="pi pi-map-marker text-blue-500 mr-1.5"></i>{{ result.label }}
            </div>
          </div>
        </div>

        <!-- 绘制模式工具栏 -->
        <div v-if="isDrawingMode && drawingType === 'waypoint'"
          class="absolute top-4 left-4 z-[1000] bg-white/95 backdrop-blur rounded-xl p-3 shadow-lg">
          <div class="text-sm font-semibold text-gray-900 mb-2">航线绘制</div>
          <div class="text-xs text-gray-500 mb-2">已添加 {{ waypoints.length }} 个航点</div>
          <div class="flex flex-col gap-2">
            <Button label="撤销" icon="pi pi-undo" @click="undoLastWaypoint" :disabled="waypoints.length === 0"
              class="!text-xs !py-1" severity="secondary" />
            <Button label="完成保存" icon="pi pi-check" @click="showCreateDialog = true" :disabled="waypoints.length < 2"
              class="!text-xs !py-1" severity="success" />
            <Button label="取消" icon="pi pi-times" @click="exitDrawingMode"
              class="!text-xs !py-1" severity="danger" />
          </div>
        </div>

        <!-- 任务详情面板 -->
        <div v-if="missionDetail && !isDrawingMode && !detailLoading"
          class="absolute top-4 right-14 z-[1000] bg-white/90 backdrop-blur rounded-xl p-3 shadow-lg text-xs space-y-1 min-w-[180px]">
          <div class="font-semibold text-gray-900 text-sm mb-1">{{ missionDetail.name }}</div>
          <div class="flex justify-between"><span class="text-gray-500">状态</span><span :class="['px-2 py-0.5 rounded-full font-semibold', statusClassMap[missionDetail.status]]">{{ statusMap[missionDetail.status] }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">航点数</span><span class="text-gray-900 font-semibold">{{ (missionDetail.waypoints || []).length }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">总距离</span><span class="text-gray-900 font-semibold">{{ formatDistance(missionDetail.totalDistance) }}</span></div>
          <div class="flex justify-between"><span class="text-gray-500">预估时长</span><span class="text-gray-900 font-semibold">{{ formatDuration(missionDetail.estimatedTime) }}</span></div>
          <div v-if="missionDetail.description" class="pt-1 border-t border-gray-200/60 text-gray-500">{{ missionDetail.description }}</div>
        </div>

        <!-- 禁飞区图层开关 -->
        <button @click="toggleFlyZones"
          class="absolute top-24 right-4 z-[1000] w-9 h-9 flex items-center justify-center rounded-lg shadow-lg transition-colors cursor-pointer"
          :class="showFlyZones ? 'bg-red-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-100'"
          title="切换禁飞区显示">
          <i class="pi pi-shield text-sm"></i>
        </button>
      </div>
    </div>

    <!-- 创建航线任务对话框 -->
    <Dialog v-model:visible="showCreateDialog" header="保存航线任务" :modal="true" :style="{ width: '400px' }">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">任务名称 *</label>
          <InputText v-model="missionForm.name" placeholder="请输入任务名称" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">任务描述</label>
          <Textarea v-model="missionForm.description" placeholder="请输入任务描述" :rows="3" class="w-full" />
        </div>
        <div class="text-xs text-gray-500">航点数: {{ waypoints.length }}</div>
      </div>
      <template #footer>
        <Button label="取消" severity="secondary" @click="showCreateDialog = false" />
        <Button label="保存" @click="saveMission" />
      </template>
    </Dialog>

    <!-- 分派/转派对话框 -->
    <Dialog v-model:visible="showAssignDialog" :header="isReassign ? '转派航线任务' : '分派航线任务'" :modal="true" :style="{ width: '400px' }">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">选择执行人 *</label>
          <Dropdown v-model="assignForm.assigneeId" :options="usersList" optionLabel="label" optionValue="value"
            placeholder="请选择用户" class="w-full" />
        </div>
      </div>
      <template #footer>
        <Button label="取消" severity="secondary" @click="showAssignDialog = false" />
        <Button :label="isReassign ? '确认转派' : '确认分派'" @click="confirmAssign" />
      </template>
    </Dialog>

    <!-- 创建禁飞区对话框 -->
    <Dialog v-model:visible="showFlyZoneCreateDialog" header="保存禁飞区" :modal="true" :style="{ width: '400px' }">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">区域名称 *</label>
          <InputText v-model="flyZoneForm.name" placeholder="请输入区域名称" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">区域类型</label>
          <Dropdown v-model="flyZoneForm.category" :options="categoryOptions" optionLabel="label" optionValue="value"
            class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">描述</label>
          <Textarea v-model="flyZoneForm.description" placeholder="区域描述" :rows="2" class="w-full" />
        </div>
      </div>
      <template #footer>
        <Button label="取消" severity="secondary" @click="showFlyZoneCreateDialog = false; if(tempDrawLayer){drawnItems.removeLayer(tempDrawLayer);tempDrawLayer=null;} isDrawingMode=false; drawingType=''" />
        <Button label="保存" @click="saveFlyZone" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
/* 航点标记样式 */
:deep(.waypoint-marker) {
  background: none !important;
  border: none !important;
}

:deep(.waypoint-icon) {
  width: 28px;
  height: 28px;
  background: #2563EB;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  font-size: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  border: 2px solid white;
}

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

/* leaflet-draw 工具栏 */
:deep(.leaflet-draw-toolbar) {
  margin-top: 10px;
}
</style>
