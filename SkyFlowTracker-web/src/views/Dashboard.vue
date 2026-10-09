<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { checkToken } from '../api/auth'
import { getDashboardData } from '../api/dashboard'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

echarts.use([
  LineChart, BarChart, PieChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent, CanvasRenderer
])

const toast = useToast()
const loading = ref(true)
const isAdmin = ref(false)

// 时间范围选择（两个图表独立）
const rangeOptions = [
  { label: '1小时', value: '1h' },
  { label: '1天', value: '1d' },
  { label: '7天', value: '7d' },
  { label: '30天', value: '30d' }
]
const detectionRange = ref('30d')
const flightRange = ref('30d')

// 概览数据
const totalFlights = ref(0)
const totalDetections = ref(0)
const totalDuration = ref(0)
const totalDevices = ref(0)
const totalUsers = ref(0)

// 图表数据
const detectionTrend = ref([])
const flightActivity = ref([])
const classDistribution = ref({})
const deviceRanking = ref([])
const flightLocations = ref([])
const userActivityRanking = ref([])

// 图表实例（用对象存储，方便复用）
const detectionTrendChart = ref(null)
const flightActivityChart = ref(null)
const classDistributionChart = ref(null)
const deviceRankingChart = ref(null)
const userRankingChart = ref(null)
let detectionTrendInstance = null
let flightActivityInstance = null
let chartInstances = []

// 地图
const mapContainer = ref(null)
let mapInstance = null

const formatDuration = (seconds) => {
  if (!seconds) return '0分钟'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return `${h}小时${m}分钟`
  return `${m}分钟`
}

const fetchData = async () => {
  try {
    const res = await getDashboardData(detectionRange.value, flightRange.value)
    if (res.code === 1 && res.data) {
      const d = res.data
      isAdmin.value = d.isAdmin
      totalFlights.value = d.totalFlights || 0
      totalDetections.value = d.totalDetections || 0
      totalDuration.value = d.totalDuration || 0
      totalDevices.value = d.totalDevices || 0
      totalUsers.value = d.totalUsers || 0
      detectionTrend.value = d.detectionTrend || []
      flightActivity.value = d.flightActivity || []
      classDistribution.value = d.classDistribution || {}
      deviceRanking.value = d.deviceRanking || []
      flightLocations.value = d.flightLocations || []
      userActivityRanking.value = d.userActivityRanking || []
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取看板数据失败', life: 3000 })
  }
}

const onDetectionRangeChange = async (value) => {
  detectionRange.value = value
  await fetchData()
  nextTick(() => initDetectionTrendChart())
}

const onFlightRangeChange = async (value) => {
  flightRange.value = value
  await fetchData()
  nextTick(() => initFlightActivityChart())
}

const initCharts = () => {
  nextTick(() => {
    initDetectionTrendChart()
    initFlightActivityChart()
    initClassDistributionChart()
    initDeviceRankingChart()
    if (isAdmin.value) initUserRankingChart()
    initMap()
  })
}

const initDetectionTrendChart = () => {
  const el = detectionTrendChart.value
  if (!el) return
  if (!detectionTrendInstance) {
    detectionTrendInstance = echarts.init(el)
    chartInstances.push(detectionTrendInstance)
  }
  const dates = detectionTrend.value.map(i => i.date)
  const values = detectionTrend.value.map(i => i.detections)
  detectionTrendInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '检测车辆数',
      type: 'line',
      smooth: true,
      data: values,
      areaStyle: { opacity: 0.15 },
      itemStyle: { color: '#3b82f6' }
    }]
  })
}

const initFlightActivityChart = () => {
  const el = flightActivityChart.value
  if (!el) return
  if (!flightActivityInstance) {
    flightActivityInstance = echarts.init(el)
    chartInstances.push(flightActivityInstance)
  }
  const dates = flightActivity.value.map(i => i.date)
  const values = flightActivity.value.map(i => i.count)
  flightActivityInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '飞行次数',
      type: 'bar',
      data: values,
      itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

const initClassDistributionChart = () => {
  const el = classDistributionChart.value
  if (!el) return
  const chart = echarts.init(el)
  chartInstances.push(chart)
  const classNames = { car: '小汽车', bus: '公交车', truck: '卡车' }
  const colors = { car: '#3b82f6', bus: '#f59e0b', truck: '#ef4444' }
  const data = Object.entries(classDistribution.value).map(([key, val]) => ({
    name: classNames[key] || key,
    value: val,
    itemStyle: { color: colors[key] || undefined }
  }))
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '0%' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{d}%' },
      data: data.length > 0 ? data : [{ name: '暂无数据', value: 0 }]
    }]
  })
}

const initDeviceRankingChart = () => {
  const el = deviceRankingChart.value
  if (!el) return
  const chart = echarts.init(el)
  chartInstances.push(chart)
  const items = [...deviceRanking.value].reverse()
  const names = items.map(i => i.model ? `${i.sn}-${i.model}` : i.sn)
  const values = items.map(i => Math.round((i.flyTime || 0) / 60))
  chart.setOption({
    tooltip: { trigger: 'axis', formatter: (p) => `${p[0].name}<br/>飞行时长: ${p[0].value} 分钟` },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '分钟' },
    yAxis: { type: 'category', data: names },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: { color: '#8b5cf6', borderRadius: [0, 4, 4, 0] }
    }]
  })
}

const initUserRankingChart = () => {
  const el = userRankingChart.value
  if (!el) return
  const chart = echarts.init(el)
  chartInstances.push(chart)
  const items = [...userActivityRanking.value].reverse()
  const names = items.map(i => i.nickName || i.userName)
  const values = items.map(i => i.flightCount)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', minInterval: 1, name: '次' },
    yAxis: { type: 'category', data: names },
    series: [{
      name: '飞行次数',
      type: 'bar',
      data: values,
      itemStyle: { color: '#f59e0b', borderRadius: [0, 4, 4, 0] }
    }]
  })
}

const initMap = () => {
  const el = mapContainer.value
  if (!el) return

  // 过滤有效坐标（排除 null 和超出合法范围的异常值）
  const validLocations = flightLocations.value.filter(
    loc => loc.lat != null && loc.lng != null
      && loc.lat >= -90 && loc.lat <= 90
      && loc.lng >= -180 && loc.lng <= 180
  )
  if (validLocations.length === 0) return

  mapInstance = L.map(el).setView([30.5, 114.4], 10)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap'
  }).addTo(mapInstance)

  const markerIcon = L.divIcon({
    className: 'flight-marker',
    html: '<div style="width:14px;height:14px;background:#3b82f6;border:2px solid #fff;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,.4);"></div>',
    iconSize: [14, 14],
    iconAnchor: [7, 7]
  })

  const bounds = []
  for (const loc of validLocations) {
    const latlng = [loc.lat, loc.lng]
    bounds.push(latlng)
    L.marker(latlng, { icon: markerIcon }).addTo(mapInstance)
      .bindPopup(`SN: ${loc.sn}<br/>时间: ${loc.startTime}`)
  }
  if (bounds.length > 0) {
    mapInstance.fitBounds(bounds, { padding: [30, 30] })
  }
}

const handleResize = () => {
  chartInstances.forEach(c => c.resize())
  if (mapInstance) mapInstance.invalidateSize()
}

onMounted(async () => {
  try {
    await checkToken()
  } catch (e) {}
  await fetchData()
  loading.value = false
  initCharts()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c.dispose())
  chartInstances = []
  if (mapInstance) { mapInstance.remove(); mapInstance = null }
})
</script>

<template>
  <div>
    <Toast />

    <div v-if="loading" class="flex justify-center py-12">
      <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
    </div>

    <div v-else>
      <!-- 概览卡片 -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6" :class="{ 'lg:grid-cols-5': isAdmin }">
        <div class="bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">总飞行次数</p>
              <p class="text-3xl font-bold text-gray-900 mt-1">{{ totalFlights }}</p>
            </div>
            <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center">
              <i class="pi pi-send text-xl text-blue-600"></i>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">检测车辆总数</p>
              <p class="text-3xl font-bold text-gray-900 mt-1">{{ totalDetections }}</p>
            </div>
            <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center">
              <i class="pi pi-car text-xl text-green-600"></i>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">总飞行时长</p>
              <p class="text-3xl font-bold text-gray-900 mt-1">{{ formatDuration(totalDuration) }}</p>
            </div>
            <div class="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center">
              <i class="pi pi-clock text-xl text-purple-600"></i>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">设备数量</p>
              <p class="text-3xl font-bold text-gray-900 mt-1">{{ totalDevices }}</p>
            </div>
            <div class="w-12 h-12 bg-orange-100 rounded-xl flex items-center justify-center">
              <i class="pi pi-box text-xl text-orange-600"></i>
            </div>
          </div>
        </div>

        <div v-if="isAdmin" class="bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-500">用户总数</p>
              <p class="text-3xl font-bold text-gray-900 mt-1">{{ totalUsers }}</p>
            </div>
            <div class="w-12 h-12 bg-pink-100 rounded-xl flex items-center justify-center">
              <i class="pi pi-users text-xl text-pink-600"></i>
            </div>
          </div>
        </div>
      </div>

      <!-- 时间范围选择 + 检测趋势 + 车辆类型 -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <div class="lg:col-span-2 bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold text-gray-900">检测趋势</h3>
            <div class="flex gap-1">
              <button v-for="opt in rangeOptions" :key="opt.value"
                @click="onDetectionRangeChange(opt.value)"
                class="px-3 py-1 text-sm rounded-lg transition-colors"
                :class="detectionRange === opt.value
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'">
                {{ opt.label }}
              </button>
            </div>
          </div>
          <div ref="detectionTrendChart" class="w-full" style="height: 300px;"></div>
        </div>
        <div class="bg-white rounded-2xl shadow-lg p-6">
          <h3 class="text-lg font-bold text-gray-900 mb-4">车辆类型分布</h3>
          <div ref="classDistributionChart" class="w-full" style="height: 300px;"></div>
        </div>
      </div>

      <!-- 飞行活动 + 设备排行 -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div class="bg-white rounded-2xl shadow-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold text-gray-900">飞行活动</h3>
            <div class="flex gap-1">
              <button v-for="opt in rangeOptions" :key="'flight-' + opt.value"
                @click="onFlightRangeChange(opt.value)"
                class="px-3 py-1 text-sm rounded-lg transition-colors"
                :class="flightRange === opt.value
                  ? 'bg-green-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'">
                {{ opt.label }}
              </button>
            </div>
          </div>
          <div ref="flightActivityChart" class="w-full" style="height: 300px;"></div>
        </div>
        <div class="bg-white rounded-2xl shadow-lg p-6">
          <h3 class="text-lg font-bold text-gray-900 mb-4">设备使用排行 Top10</h3>
          <div ref="deviceRankingChart" class="w-full" style="height: 300px;"></div>
        </div>
      </div>

      <!-- 飞行位置地图 -->
      <div class="bg-white rounded-2xl shadow-lg p-6 mb-6">
        <h3 class="text-lg font-bold text-gray-900 mb-4">飞行位置分布</h3>
        <div ref="mapContainer" class="w-full rounded-lg" style="height: 400px;"></div>
        <p v-if="flightLocations.length === 0" class="text-gray-400 text-center py-12">暂无飞行位置数据</p>
      </div>

      <!-- 用户活跃排行（仅管理员） -->
      <div v-if="isAdmin" class="bg-white rounded-2xl shadow-lg p-6 mb-6">
        <h3 class="text-lg font-bold text-gray-900 mb-4">用户活跃排行 Top10</h3>
        <div ref="userRankingChart" class="w-full" style="height: 300px;"></div>
      </div>
    </div>
  </div>
</template>
