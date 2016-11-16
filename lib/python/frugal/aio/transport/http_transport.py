import base64

from aiohttp.client import ClientSession
from thrift.transport.TTransport import TTransportException

from frugal.aio.transport import FRegistryTransport
from frugal.exceptions import FMessageSizeException


class FHttpTransport(FRegistryTransport):
    """
    FHttpTransport is an FTransport that uses http as the underlying transport.
    This allows messages of arbitrary sizes to be sent and received.
    """
    def __init__(self, url, request_capacity=0, response_capacity=0):
        """
        Create an HTTP transport.

        Args:
            url: The url to send requests to.
            request_capacity: The maximum size allowed to be written in a
                              request. Set to 0 for no size restrictions.
            response_capacity: The maximum size allowed to be read in a
                               response. Set to 0 for no size restrictions
        """
        super().__init__(request_capacity)
        self._url = url

        self._headers = {
            'content-type': 'application/x-frugal',
            'content-transfer-encoding': 'base64',
            'accept': 'application/x-frugal',
        }
        if response_capacity > 0:
            self._headers['x-frugal-payload-limit'] = str(response_capacity)

    def is_open(self):
        """Always returns True"""
        return True

    async def open(self):
        """No-op"""
        pass

    async def close(self):
        """No-op"""
        pass

    async def send(self, data):
        """
        Write the current buffer over the network and execute the callback set
        in the registry with the response.
        """
        if len(data) > self._max_message_size > 0:
            raise FMessageSizeException.for_request(
                'Message exceeds {0} bytes, was {1} bytes'.format(
                    self._max_message_size, len(data)))

        encoded = base64.b64encode(data)
        status, text = await self._make_request(encoded)
        if status == 413:
            raise FMessageSizeException.for_response(
                'response was too large for the transport')

        if status >= 300:
            raise TTransportException(
                type=TTransportException.UNKNOWN,
                message='request errored with code {0} and message {1}'.format(
                    status, str(text)
                )
            )

        decoded = base64.b64decode(text)
        if len(decoded) < 4:
            raise TTransportException(type=TTransportException.UNKNOWN,
                                      message='invalid frame size')

        if len(decoded) == 4:
            if any(decoded):
                raise TTransportException(type=TTransportException.UNKNOWN,
                                          message='missing data')
            # One-way method, drop response
            return

        await self.execute_frame(decoded[4:])

    async def _make_request(self, data):
        """
        Helper method to make a request over the network.

        Args:
            data: The data to be sent over the network.
        Return:
            The status code and body of the response.
        """
        with ClientSession() as session:
            async with session.post(self._url,
                                    data=data,
                                    headers=self._headers) as response:
                return response.status, await response.content.read()
