import { render, screen, userEvent } from '@testing-library/react-native';
import { Button } from '../Button';
import { MoneyText } from '../MoneyText';
import { Row } from '../Row';
import { SegmentedControl } from '../SegmentedControl';
import { TextField } from '../TextField';

describe('UI components', () => {
  it('MoneyText formats amounts', async () => {
    await render(<MoneyText amount={2975342} currency="VND" />);
    expect(screen.getByText('2.975.342 ₫')).toBeOnTheScreen();
  });

  it('Button calls onPress and is disabled while loading', async () => {
    const onPress = jest.fn();
    const user = userEvent.setup();
    const { rerender } = await render(<Button title="Lưu" onPress={onPress} />);

    await user.press(screen.getByRole('button', { name: 'Lưu' }));
    expect(onPress).toHaveBeenCalledTimes(1);

    await rerender(<Button title="Lưu" onPress={onPress} loading />);
    expect(screen.getByRole('button', { name: 'Lưu' })).toBeDisabled();
  });

  it('TextField shows its error', async () => {
    await render(<TextField label="Email" value="" onChangeText={jest.fn()} error="Email không hợp lệ" />);
    expect(screen.getByText('Email không hợp lệ')).toBeOnTheScreen();
  });

  it('SegmentedControl marks the selected option and reports changes', async () => {
    const onChange = jest.fn();
    const user = userEvent.setup();
    await render(<SegmentedControl value="A" onChange={onChange}
      options={[{ value: 'A', label: 'Alpha' }, { value: 'B', label: 'Beta' }]} />);

    expect(screen.getByRole('radio', { name: 'Alpha' })).toBeChecked();
    await user.press(screen.getByRole('radio', { name: 'Beta' }));
    expect(onChange).toHaveBeenCalledWith('B');
  });
});

describe('Row', () => {
  it('wraps composite text children', async () => {
    const date = '01/07/2025';
    await render(<Row label="Ngày">{date}{' (còn 5 ngày)'}</Row>);
    expect(screen.getByText('01/07/2025 (còn 5 ngày)')).toBeOnTheScreen();
  });
});
