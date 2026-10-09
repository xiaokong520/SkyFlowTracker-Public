import assert from 'node:assert/strict'
import test from 'node:test'
import { toMediaUrl } from '../src/utils/media.js'

test('maps absolute video URLs without depending on a deployment hostname', () => {
  assert.equal(toMediaUrl('http://192.0.2.10/SkyFlowTracker/videos/output/a.mp4'), '/nginx/videos/output/a.mp4')
  assert.equal(toMediaUrl('https://media.example.com/SkyFlowTracker/videos/a.mp4?t=1#clip'), '/nginx/videos/a.mp4?t=1#clip')
})
test('supports relative paths and already proxied URLs', () => {
  assert.equal(toMediaUrl('/SkyFlowTracker/videos/a.mp4'), '/nginx/videos/a.mp4')
  assert.equal(toMediaUrl('videos/output/a.mp4'), '/nginx/videos/output/a.mp4')
  assert.equal(toMediaUrl('/nginx/videos/a.mp4'), '/nginx/videos/a.mp4')
})
test('respects path boundaries and configurable media paths', () => {
  assert.equal(toMediaUrl('https://example.com/SkyFlowTrackerOther/a.mp4'), 'https://example.com/SkyFlowTrackerOther/a.mp4')
  assert.equal(toMediaUrl('https://example.com/media/a.mp4', '/media/'), '/nginx/a.mp4')
  assert.equal(toMediaUrl('https://example.com/other.mp4'), 'https://example.com/other.mp4')
})
test('rejects missing, invalid or unsafe values', () => {
  for (const value of [undefined, null, '', ' ', 42, 'http://[', 'javascript:alert(1)', 'file:///tmp/a.mp4']) {
    assert.equal(toMediaUrl(value), '')
  }
})
