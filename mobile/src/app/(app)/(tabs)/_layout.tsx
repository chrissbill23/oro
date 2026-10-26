import { Tabs } from 'expo-router';
import { CapsuleTabBar } from '../../../components/Nav';
import { useRequests } from '../../../lib/queries';

/** Chats, Requests, You: the capsule tab bar of the design, floating over the lists */
export default function TabsLayout() {
  const requests = useRequests();
  const waiting = requests.data?.length ?? 0;
  return (
    <Tabs tabBar={(props) => <CapsuleTabBar {...props} />} screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: 'transparent' } }}>
      <Tabs.Screen name="index" options={{ title: 'Chats' }} />
      <Tabs.Screen name="requests" options={{ title: 'Requests', tabBarBadge: waiting > 0 ? waiting : undefined }} />
      <Tabs.Screen name="you" options={{ title: 'You' }} />
    </Tabs>
  );
}
