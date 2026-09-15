import { beforeEach, describe, expect, it } from 'vitest';
import { MSG, parseDump, presetsEqual } from '../src/pacer';
import {
  defaultDataFor,
  initialSlots,
  isSlotEdited,
  isSlotInSync,
  pendingSummary,
  setDeviceTruth,
  slotPendingParts,
  useEditor,
} from '../src/store/editor';
import { buildBitwigLooperPreset, LOOPER_LABELS } from '../src/templates/bitwigLooper';
import { fixture } from './helpers';

const factory = parseDump(fixture('ALL.factory.bin'));
const A1 = factory.presets.get(1)!.preset;
const A2 = factory.presets.get(2)!.preset;
const get = () => useEditor.getState();

beforeEach(() => {
  useEditor.setState({
    slots: initialSlots(),
    past: [],
    future: [],
    clipboard: null,
    preview: null,
    selectedSlot: 19,
    selection: { kind: 'control', key: 'SW1' },
  });
});

describe('editor store', () => {
  it('tracks edits against the device and counts pending messages', () => {
    get().loadFromDevice([{ index: 1, preset: A1 }]);
    expect(isSlotEdited(get().slots[1])).toBe(false);
    expect(isSlotInSync(get().slots[1])).toBe(true);
    expect(pendingSummary(get().slots)).toEqual({ slots: [], messages: 0 });

    get().updateStep(1, 'SWA', 1, { data: [1, 2, 3] });
    get().setName(1, 'HELLO');
    expect(isSlotEdited(get().slots[1])).toBe(true);
    expect(slotPendingParts(get().slots[1], 1)).toHaveLength(2);
    expect(pendingSummary(get().slots)).toEqual({ slots: [1], messages: 2 });

    get().markWritten(1, get().slots[1].preset!);
    expect(isSlotEdited(get().slots[1])).toBe(false);
    expect(pendingSummary(get().slots).messages).toBe(0);
  });

  it('sends the whole preset when the device content is unknown', () => {
    get().loadPresets([{ index: 19, preset: buildBitwigLooperPreset('two-colour'), labels: LOOPER_LABELS }], 'template', 'Template');
    expect(isSlotEdited(get().slots[19])).toBe(true);
    expect(slotPendingParts(get().slots[19], 19)).toHaveLength(189);
    setDeviceTruth([{ index: 19, preset: A1 }], false);
    expect(slotPendingParts(get().slots[19], 19).length).toBeLessThan(189);
  });

  it('does not mark imported files as pending', () => {
    get().loadPresets([{ index: 3, preset: A2 }], 'file', 'Import');
    expect(isSlotEdited(get().slots[3])).toBe(false);
    expect(pendingSummary(get().slots).messages).toBe(0);
  });

  it('undoes and redoes edits without forgetting device knowledge', () => {
    get().loadFromDevice([{ index: 1, preset: A1 }]);
    get().setControlMode(1, 'SW2', 2);
    const edited = get().slots[1].preset!;
    get().markWritten(1, edited);

    get().undo();
    expect(get().slots[1].preset!.controls.SW2.mode).toBe(A1.controls.SW2.mode);
    expect(get().slots[1].device).toBe(edited); // still what we wrote
    expect(slotPendingParts(get().slots[1], 1)).toHaveLength(1);

    get().redo();
    expect(get().slots[1].preset!.controls.SW2.mode).toBe(2);
    expect(get().future).toHaveLength(0);

    get().undo();
    get().undo();
    expect(get().slots[1].preset).toBeNull();
  });

  it('coalesces rapid edits of the same field', () => {
    get().loadFromDevice([{ index: 1, preset: A1 }]);
    get().updateStep(1, 'SW1', 0, { data: [1, 0, 0] });
    get().updateStep(1, 'SW1', 0, { data: [12, 0, 0] });
    get().updateStep(1, 'SW1', 0, { data: [127, 0, 0] });
    expect(get().past).toHaveLength(2);
    get().updateStep(1, 'SW1', 0, { channel: 5 });
    expect(get().past).toHaveLength(3);
    get().undo();
    get().undo();
    expect(presetsEqual(get().slots[1].preset, A1)).toBe(true);
  });

  it('applies sensible defaults when the message type changes', () => {
    get().loadFromDevice([{ index: 1, preset: A1 }]);
    get().setStepType(1, 'SWB', 3, MSG.SW_CC_TRIGGER);
    const step = get().slots[1].preset!.controls.SWB.steps[3];
    expect(step).toMatchObject({ msgType: MSG.SW_CC_TRIGGER, data: [0, 127, 0], active: true });
    get().setStepType(1, 'SWB', 3, MSG.SW_CC_TOGGLE);
    get().updateStep(1, 'SWB', 3, { data: [44, 127, 0] });
    get().setStepType(1, 'SWB', 3, MSG.SW_CC_STEP);
    expect(get().slots[1].preset!.controls.SWB.steps[3].data).toEqual([44, 0, 127]);
    get().setStepType(1, 'SWB', 3, MSG.OFF);
    expect(get().slots[1].preset!.controls.SWB.steps[3].active).toBe(false);
    expect(defaultDataFor(MSG.SW_NOTE, { msgType: MSG.OFF, data: [0, 0, 0] })).toEqual([60, 127, 0]);
  });

  it('copies, pastes, swaps and duplicates presets', () => {
    get().loadFromDevice([
      { index: 1, preset: A1 },
      { index: 2, preset: A2 },
    ]);
    get().copySlot(1);
    get().pasteSlot(5);
    expect(get().slots[5].preset).toBe(A1);
    expect(isSlotEdited(get().slots[5])).toBe(true);

    get().swapSlots(1, 2);
    expect(get().slots[1].preset).toBe(A2);
    expect(get().slots[2].preset).toBe(A1);

    const target = get().duplicateSlot(2);
    expect(target).toBe(3);
    expect(get().slots[3].preset).toBe(A1);
    expect(get().selectedSlot).toBe(3);

    get().copySlotTo(3, 20);
    expect(get().slots[20].preset).toBe(A1);
  });

  it('never duplicates into D6', () => {
    for (let i = 1; i <= 23; i++) get().loadPresets([{ index: i, preset: A1 }], 'file', 'fill');
    expect(get().duplicateSlot(23)).toBe(24);
    get().loadPresets([{ index: 1, preset: A2 }], 'file', 'x');
    useEditor.setState((s) => ({ slots: s.slots.map((slot, i) => (i === 10 ? { ...slot, preset: null } : slot)) }));
    expect(get().duplicateSlot(23)).toBe(10);
  });

  it('applies a template preview as a single undoable step', () => {
    const preset = buildBitwigLooperPreset('multi-colour');
    get().startPreview({ preset, labels: LOOPER_LABELS, slot: 19, title: 'Bitwig Looper' });
    expect(get().slots[19].preset).toBeNull();
    get().applyPreview();
    expect(get().preview).toBeNull();
    expect(get().slots[19].preset).toBe(preset);
    expect(get().slots[19].labels.SW1).toBe('LOOP 1');
    expect(get().past).toHaveLength(1);
    get().undo();
    expect(get().slots[19].preset).toBeNull();
  });
});
