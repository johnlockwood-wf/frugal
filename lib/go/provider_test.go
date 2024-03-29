package frugal

import (
	"sync"
	"testing"

	"git.apache.org/thrift.git/lib/go/thrift"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

type mockFPublisherTransportFactory struct {
	mock.Mock
}

func (m *mockFPublisherTransportFactory) GetTransport() FPublisherTransport {
	return m.Called().Get(0).(FPublisherTransport)
}

type mockFSubscriberTransportFactory struct {
	mock.Mock
}

func (m *mockFSubscriberTransportFactory) GetTransport() FSubscriberTransport {
	return m.Called().Get(0).(FSubscriberTransport)
}

type mockTProtocolFactory struct {
	mock.Mock
	sync.Mutex
}

func (m *mockTProtocolFactory) GetProtocol(tr thrift.TTransport) thrift.TProtocol {
	m.Lock()
	defer m.Unlock()
	return m.Called(tr).Get(0).(thrift.TProtocol)
}

func (m *mockTProtocolFactory) AssertExpectations(t *testing.T) {
	m.Lock()
	defer m.Unlock()
	m.Mock.AssertExpectations(t)
}

type mockFProcessor struct {
	mock.Mock
	sync.Mutex
}

func (m *mockFProcessor) Process(in, out *FProtocol) error {
	m.Lock()
	defer m.Unlock()
	return m.Called(in, out).Error(0)
}

func (m *mockFProcessor) AddMiddleware(middleware ServiceMiddleware) {}

func (m *mockFProcessor) Annotations() map[string]map[string]string {
	return m.Called().Get(0).(map[string]map[string]string)
}

func (m *mockFProcessor) AssertExpectations(t *testing.T) {
	m.Lock()
	defer m.Unlock()
	m.Mock.AssertExpectations(t)
}

func TestScopeProviderNew(t *testing.T) {
	mockPublisherTransportFactory := new(mockFPublisherTransportFactory)
	mockSubscriberTransportFactory := new(mockFSubscriberTransportFactory)
	mockTProtocolFactory := new(mockTProtocolFactory)
	protoFactory := NewFProtocolFactory(mockTProtocolFactory)
	provider := NewFScopeProvider(mockPublisherTransportFactory, mockSubscriberTransportFactory, protoFactory)
	publisherTransport := new(fNatsPublisherTransport)
	subscriberTransport := new(fNatsSubscriberTransport)
	mockPublisherTransportFactory.On("GetTransport").Return(publisherTransport)
	mockSubscriberTransportFactory.On("GetTransport").Return(subscriberTransport)

	ptransport, pubProtoFactory := provider.NewPublisher()
	stransport, subProtoFactory := provider.NewSubscriber()
	assert.Equal(t, publisherTransport, ptransport)
	assert.Equal(t, subscriberTransport, stransport)
	assert.Equal(t, pubProtoFactory, protoFactory)
	assert.Equal(t, subProtoFactory, protoFactory)
	mockPublisherTransportFactory.AssertExpectations(t)
	mockSubscriberTransportFactory.AssertExpectations(t)
	mockTProtocolFactory.AssertExpectations(t)
}
