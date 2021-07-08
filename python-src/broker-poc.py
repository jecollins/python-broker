# Python broker proof-of-concept
# Requires sample-broker.jar in the current directory -- this is where
# we get the classpath.

import jpype
import jpype.imports
from jpype.types import *

jpype.startJVM(classpath = ['sample-broker-1.9.0-SNAPSHOT.jar'])

from org.powertac.samplebroker.core import BrokerRunner
from org.powertac.common import Competition, CustomerInfo,Order, Orderbook, TariffSpecification, Rate
from org.powertac.common import TimeService, WeatherReport, WeatherForecast, WeatherForecastPrediction
from org.powertac.common.repo import BrokerRepo, TimeslotRepo, CustomerRepo, TariffRepo
from org.powertac.common.repo import WeatherReportRepo, WeatherForecastRepo

argList = JArray('java.lang.String', 1)
args = argList(['--repeat-count', '0'])
#args[0] = '--log-suffix'
#args[1] = 'pyBroker'
runner = BrokerRunner()
runner.processCmdLine (args)
